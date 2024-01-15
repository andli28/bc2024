package mainbot;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

enum Spec {
    ATT,
    BUILD,
    HEAL,
    NONE
}

// from
// https://www.geeksforgeeks.org/creating-a-user-defined-printable-pair-class-in-java/
// since im lazy
class Pair {
    int first, second;

    // constructor for assigning values
    Pair(int first, int second) {
        this.first = first;
        this.second = second;
    }

    // printing the pair class
    @Override
    public String toString() {
        return first + "," + second;
    }
}

public class Comms {
    // comms[0:2][15] ally flag exists
    // comms[0:2][11:6], [5:0] x, y coords of default ally flag locs
    // comms[3:5][15] ally flag exists
    // comms[3:5][11:6], [5:0] x, y coords of current ally flag locs
    // comms[6:8][15] enemy flag exists
    // comms[6:8][11:6], [5:0] x, y coords of default enemy flag locs
    // comms[9:11][15] enemy flag exists
    // comms[9:11][11:6], [5:0] x, y coords of current enemy flag locs
    // comms[12] ally attack spec ducks
    // comms[13] ally build spec ducks
    // comms[14] ally heal spec ducks
    // comms[15:18][15], [11:6], [5:0] exists, x, y of 4 randomly sampled enemies
    // comms[19] unit count for random sampling and id sequencing
    // comms[20] enemy alive count(overestimate)

    // this ordered wack since flag ids only added later
    // comms[21:23] flag ids of ally flags, all ally flag info follows this order
    // ex: comms[0] will always be same flag as comms[3] and comms[21]
    // comms[24:26] flag ids of enemy flags, all enemy flag info follows this order

    // comms[27:29][15][11:6][5:0] closest enemy loc to each ally flag(current if
    // exist, otherwise default)

    public static final int[] ALLY_DEFAULT_FLAG_INDICES = { 0, 1, 2 };
    public static final int[] ALLY_CURRENT_FLAG_INDICES = { 3, 4, 5 };
    public static final int[] ENEMY_DEFAULT_FLAG_INDICES = { 6, 7, 8 };
    public static final int[] ENEMY_CURRENT_FLAG_INDICES = { 9, 10, 11 };

    public static RobotController rc;
    public static int[] comms = new int[64];
    public static int shortId;

    public static RobotInfo[] nearbyEnemies;
    public static FlagInfo[] nearbyFlags;
    public static Team carrying;
    public static Spec prevSpec = Spec.NONE;
    // killed enemy unit respawn tracking
    public static int turnKillCount = 0;
    public static LinkedList<Pair> respawnTimer = new LinkedList<>();
    public static MapLocation prevEndTurnLoc = null;

    // comms indices you are in charge of refreshing
    public static int[] refreshIdxs = new int[8];
    public static int refreshPtr = -1;

    public static void receive() throws GameActionException {
        // yea yea unroll this later
        for (int i = 64; --i >= 0;) {
            comms[i] = rc.readSharedArray(i);
        }
    }

    public static void write(int index, int val) throws GameActionException {
        // check if info to write is already there and if can write
        if (rc.readSharedArray(index) != val && rc.canWriteSharedArray(index, val)) {
            rc.writeSharedArray(index, val);
        }
    }

    // sequence unit ids(0-49) assumes starts at 0
    public static void sequence() throws GameActionException {
        shortId = comms[19];
        write(19, shortId + 1);
    }

    // init comm values updated by first unit
    public static void initialize() throws GameActionException {
        // this should be called round 1 before update and so first unit sees 0(before
        // sequence)
        if (comms[19] == 0) {
            write(20, 50);
        }
    }

    public static void update() throws GameActionException {
        // these can be called regardless of dead or alive
        sequence();

        // refresh the indices you are in charge of(enemy locs mostly)
        // logic for this instead of end of turn clear is so that units that execute
        // code first in turn(shortid = 1) have sufficient info
        for (; refreshPtr >= 0; refreshPtr--) {
            write(refreshIdxs[refreshPtr], 0);
        }

        // current spec count
        Spec curSpec;
        if (rc.getLevel(SkillType.ATTACK) >= 4) {
            curSpec = Spec.ATT;
        } else if (rc.getLevel(SkillType.BUILD) >= 4) {
            curSpec = Spec.BUILD;
        } else if (rc.getLevel(SkillType.HEAL) >= 4) {
            curSpec = Spec.HEAL;
        } else {
            curSpec = Spec.NONE;
        }

        // if cur spec different from prev spec update counter
        if (curSpec != prevSpec) {
            switch (prevSpec) {
                case ATT:
                    write(12, comms[12] - 1);
                    break;
                case BUILD:
                    write(13, comms[13] - 1);
                    break;
                case HEAL:
                    write(14, comms[14] - 1);
                    break;
            }
            switch (curSpec) {
                case ATT:
                    write(12, comms[12] + 1);
                    break;
                case BUILD:
                    write(13, comms[13] + 1);
                    break;
                case HEAL:
                    write(14, comms[14] + 1);
                    break;
            }
        }

        // sense updating for alive guys only
        if (rc.isSpawned()) {
            // reformat to extract this
            // optimally 1 call pre and post move
            nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            sampleRandomEnemies();
            nearbyFlags = rc.senseNearbyFlags(-1);
            updateFlagLocs();
            updateCurrFlags();
            updateClosestEnemyToAllyFlags();
        }
        prevEndTurnLoc = rc.isSpawned() ? rc.getLocation() : null;

        // kill count
        if (turnKillCount > 0) {
            respawnTimer.add(new Pair(rc.getRoundNum() + GameConstants.JAILED_ROUNDS, turnKillCount));
        }
        // update global enemy estimate
        int delta = -turnKillCount;
        // go through respawn q, for prev killed units that could have respawned add
        // them back to global q
        while (respawnTimer.size() > 0) {
            Pair nextRespawn = respawnTimer.peek();
            if (nextRespawn.first <= rc.getRoundNum()) {
                respawnTimer.remove();
                delta += nextRespawn.second;
            } else {
                break;
            }
        }
        write(20, comms[20] + delta);
        turnKillCount = 0;
        prevSpec = curSpec;

        // clear comms if last unit for next turn
        if (shortId == 49) {
            // clear unit spec, sequence
            write(19, 0);
            // debug flag locations
            // System.out.println("aid1: " + comms[21] + "\naid2: " + comms[22] + "\naid3: "
            // + comms[23] + "\nafd1: "
            // + decodeLoc(comms[0]) + "\nafd2: " + decodeLoc(comms[1]) + "\nafd3: "
            // + decodeLoc(comms[2]) + "\nafc1: " + decodeLoc(comms[3]) + "\nafc2: " +
            // decodeLoc(comms[4])
            // + "\nafc3: " + decodeLoc(comms[5]));
            // System.out.println("eid1: " + comms[24] + "\neid2: " + comms[25] + "\neid3: "
            // + comms[26] + "\nefd1: "
            // + decodeLoc(comms[6]) + "\nefd2: " + decodeLoc(comms[7]) + "\nefd3: "
            // + decodeLoc(comms[8]) + "\nefc1: " + decodeLoc(comms[9]) + "\nefc2: " +
            // decodeLoc(comms[10])
            // + "\nefc3: " + decodeLoc(comms[11]));
        }
    }

    public static int getAllyAttackSpecs() {
        return comms[12];
    }

    public static int getAllyBuildSpecs() {
        return comms[13];
    }

    public static int getAllyHealSpecs() {
        return comms[14];
    }

    public static int getEnemyCount() throws GameActionException {
        return comms[20];
    }

    // removes all occurences of a value from indices if exists
    static void removeValue(int val, int[] indices) throws GameActionException {
        for (int i = indices.length; --i >= 0;) {
            int idx = indices[i];
            if (comms[idx] == val) {
                write(idx, 0);
            }
        }
    }

    // helper that writes a loc to the first available index in indices if not there
    // already
    static int writeFlagLoc(MapLocation loc, int[] indices) throws GameActionException {
        int firstAvail = -1;
        for (int i = indices.length; --i >= 0;) {
            int idx = indices[i];
            if (comms[idx] == 0) {
                firstAvail = idx;
            } else {
                if (decodeLoc(comms[idx]).equals(loc))
                    return -1;
            }
        }
        if (firstAvail == -1)
            return -1;
        write(firstAvail, encodeLoc(loc));
        return firstAvail;
    }

    // matches read id to recorded flag id index(both enemy and ally)
    static int getFlagIndexFromID(int flagID) {
        for (int i = 3; --i >= 0;) {
            if (comms[i + 21] == flagID || comms[i + 24] == flagID)
                return i;
        }
        return -1;
    }

    public static void flagDrop(FlagInfo finfo) throws GameActionException {
        // handle changing of default flags during ally setup
        int id = finfo.getID();
        if (finfo.getTeam() == rc.getTeam()) {
            // flagId should be between 0 and 2
            int flagId = getFlagIndexFromID(id);
            write(flagId, encodeLoc(finfo.getLocation()));
        }
    }

    // default locs are persistent(except ally pickup + move in setup phase)
    // current locs clear every round
    public static void updateFlagLocs() throws GameActionException {
        for (int i = nearbyFlags.length; --i >= 0;) {
            FlagInfo fi = nearbyFlags[i];
            // setup phase default ally
            if (fi.getTeam() == rc.getTeam()) {
                if (rc.getRoundNum() < 200 && !fi.isPickedUp()) {
                    // default flag loc, record into comms if not there already and assoc id slot
                    int idx = getFlagIndexFromID(fi.getID());
                    if (idx == -1) {
                        int firstFree = writeFlagLoc(fi.getLocation(), ALLY_DEFAULT_FLAG_INDICES);
                        write(21 + firstFree, fi.getID());
                    }
                } else if (rc.getRoundNum() > 200) {
                    // use id to put in correct slot
                    int idx = getFlagIndexFromID(fi.getID());
                    // if our ducks have eyes this will always true
                    if (idx != -1) {
                        write(3 + idx, encodeLoc(fi.getLocation()));
                    }
                }
            } else {
                // default/current enemy flag loc
                if (!fi.isPickedUp()) {
                    // use id to see if new flag or not, assoc id if new
                    MapLocation fl = fi.getLocation();
                    int idx = getFlagIndexFromID(fi.getID());
                    if (idx == -1) {
                        // new default enemy flag found! naisu!
                        int firstFree = writeFlagLoc(fi.getLocation(), ENEMY_DEFAULT_FLAG_INDICES);
                        idx = firstFree - 6;
                        write(24 + idx, fi.getID());
                    }
                    write(9 + idx, encodeLoc(fi.getLocation()));
                }
            }

            // if carrying enemy flag continuously refresh ping
            if (rc.getRoundNum() > 200 && rc.hasFlag() && fi.getLocation().equals(rc.getLocation())) {
                if (fi.getTeam() != rc.getTeam()) {
                    // enemy current pings persistent after death to avoid default dup(check
                    // currents)
                    int idx = getFlagIndexFromID(fi.getID());
                    if (idx != -1) {
                        write(9 + idx, encodeLoc(fi.getLocation()));
                    }
                }
            }
        }
    }

    // update curr flags by invalidating dissapeared flags
    public static void updateCurrFlags() throws GameActionException {
        for (int i = 3; --i >= 0;) {
            // enemy flags
            MapLocation loc = decodeLoc(comms[9 + i]);
            // can only invalidate comm if you can see the loc
            if (loc == null || loc.distanceSquaredTo(rc.getLocation()) > GameConstants.VISION_RADIUS_SQUARED)
                continue;
            boolean validComm = false;
            for (int j = nearbyFlags.length; --j >= 0;) {
                if (nearbyFlags[j].getLocation().equals(loc)) {
                    validComm = true;
                }
            }
            if (!validComm)
                write(9 + i, 0);
        }

        for (int i = 3; --i >= 0;) {
            // ally flags
            MapLocation loc = decodeLoc(comms[3 + i]);
            if (loc == null || loc.distanceSquaredTo(rc.getLocation()) > GameConstants.VISION_RADIUS_SQUARED)
                continue;
            boolean validComm = false;
            for (int j = nearbyFlags.length; --j >= 0;) {
                if (nearbyFlags[j].getLocation().equals(loc)) {
                    validComm = true;
                }
            }
            if (!validComm)
                write(3 + i, 0);
        }
    }

    public static MapLocation[] getDefaultAllyFlagLocations() {
        return new MapLocation[] { decodeLoc(comms[0]), decodeLoc(comms[1]), decodeLoc(comms[2]) };
    }

    // can have null entries
    public static MapLocation[] getCurrentAllyFlagLocations() {
        return new MapLocation[] { decodeLoc(comms[3]), decodeLoc(comms[4]), decodeLoc(comms[5]) };
    }

    // can have null entries if we have not seen em
    public static MapLocation[] getDefaultEnemyFlagLocations() {
        return new MapLocation[] { decodeLoc(comms[6]), decodeLoc(comms[7]), decodeLoc(comms[8]) };
    }

    // can have null entries
    public static MapLocation[] getCurrentEnemyFlagLocations() {
        return new MapLocation[] { decodeLoc(comms[9]), decodeLoc(comms[10]), decodeLoc(comms[11]) };
    }

    // returns arr of size 3 containing displaced enemy flag current locs, can
    // contain null
    public static MapLocation[] getDisplacedEnemyFlags() {
        MapLocation[] displacedEnemyFlags = new MapLocation[] { null, null, null };
        MapLocation[] defaultEnemy = getDefaultEnemyFlagLocations();
        MapLocation[] currentEnemy = getCurrentEnemyFlagLocations();
        for (int i = 3; --i >= 0;) {
            if (defaultEnemy[i] != null && !defaultEnemy[i].equals(currentEnemy[i])) {
                displacedEnemyFlags[i] = currentEnemy[i];
            }
        }
        return displacedEnemyFlags;
    }

    public static MapLocation closestDisplacedEnemyFlag() throws GameActionException {
        MapLocation closest = null;
        int dist = 9999;
        MapLocation src = rc.getLocation();
        MapLocation[] displayedEnemyFlags = getDisplacedEnemyFlags();
        for (int i = 3; --i >= 0;) {
            MapLocation loc = displayedEnemyFlags[i];
            if (loc == null)
                continue;
            if (closest == null || dist > Pathfinder.travelDistance(loc, src)) {
                closest = loc;
                dist = Pathfinder.travelDistance(loc, src);
            }
        }
        return closest;
    }

    // returns arr of size 3 containing displaced ally flag current locations, can
    // contain null entries
    public static MapLocation[] getDisplacedAllyFlags() {
        MapLocation[] displacedAllyFlags = new MapLocation[] { null, null, null };
        MapLocation[] defaultAlly = getDefaultAllyFlagLocations();
        MapLocation[] currentAlly = getCurrentAllyFlagLocations();
        for (int i = 3; --i >= 0;) {
            if (defaultAlly[i] != null && !defaultAlly[i].equals(currentAlly[i])) {
                displacedAllyFlags[i] = currentAlly[i];
            }
        }
        return displacedAllyFlags;
    }

    public static MapLocation closestDisplacedAllyFlag() throws GameActionException {
        MapLocation closest = null;
        int dist = 9999;
        MapLocation src = rc.getLocation();
        MapLocation[] displayedAllyFlags = getDisplacedAllyFlags();
        for (int i = 3; --i >= 0;) {
            MapLocation loc = displayedAllyFlags[i];
            if (loc == null)
                continue;
            if (closest == null || dist > Pathfinder.travelDistance(loc, src)) {
                closest = loc;
                dist = Pathfinder.travelDistance(loc, src);
            }
        }
        return closest;
    }

    public static void clearRandomEnemies() throws GameActionException {
        write(15, 0);
        write(16, 0);
        write(17, 0);
        write(18, 0);
    }

    public static void sampleRandomEnemies() throws GameActionException {
        if (nearbyEnemies.length > 0 && rc.canWriteSharedArray(63, 0)) {
            for (int i = 4; --i >= 0;) {
                RobotInfo r = nearbyEnemies[RobotPlayer.rng.nextInt(nearbyEnemies.length)];
                MapLocation currSight = decodeLoc(comms[15 + i]);
                // TODO now that enemy sighting clear is controlled by writer idt this pr calc
                // is correct
                if (currSight == null || (RobotPlayer.rng.nextDouble() < 1.f / 50)) {
                    write(15 + i, encodeLoc(r.getLocation()));
                    refreshIdxs[++refreshPtr] = 15 + i;
                    break;
                }
            }
        }
    }

    // returns arr of len 4 of sampled enemies, can contain null entries
    public static MapLocation[] getSampledEnemies() throws GameActionException {
        MapLocation[] enemies = new MapLocation[4];
        for (int i = 4; --i >= 0;) {
            enemies[i] = decodeLoc(comms[15 + i]);
        }
        return enemies;
    }

    public static MapLocation getClosestSampleEnemy() throws GameActionException {
        MapLocation closestSampleEnemyLoc = null;
        MapLocation[] sampledEnemies = getSampledEnemies();
        for (int i = 4; --i >= 0;) {
            MapLocation enemy = sampledEnemies[i];
            if (closestSampleEnemyLoc == null
                    || (Pathfinder.travelDistance(closestSampleEnemyLoc, rc.getLocation()) > Pathfinder
                            .travelDistance(enemy, rc.getLocation()))) {
                closestSampleEnemyLoc = enemy;
            }
        }
        return closestSampleEnemyLoc;
    }

    // returns arr of size 3 containing enemy locations closest to each ally
    // flag(current if exists, otherwise default), can have null entries
    public static MapLocation[] getClosestEnemyToAllyFlags() throws GameActionException {
        return new MapLocation[] { decodeLoc(comms[27]), decodeLoc(comms[28]), decodeLoc(comms[29]) };
    }

    // absurdlyLongCamelCaseFunctionNameShowcaseNumberOne.png
    public static int[] getClosestEnemyDistanceToAllyFlags() throws GameActionException {
        int[] dists = { 9999, 9999, 9999 };
        MapLocation[] closestEnemies = getClosestEnemyToAllyFlags();
        MapLocation[] currentAllyFlagLocs = getCurrentAllyFlagLocations();
        MapLocation[] defaultAllyFlagLocs = getDefaultAllyFlagLocations();
        for (int i = 3; --i >= 0;) {
            MapLocation flagRef = currentAllyFlagLocs[i] == null ? defaultAllyFlagLocs[i] : currentAllyFlagLocs[i];
            MapLocation enemyLoc = closestEnemies[i];
            if (enemyLoc != null)
                dists[i] = Pathfinder.travelDistance(enemyLoc, flagRef);
        }
        return dists;
    }

    public static void updateClosestEnemyToAllyFlags() throws GameActionException {
        // calculate each enemy's dist to each ally flag, write if closer than whats in
        // comms, remember to refresh by urself next turn so all teammates get info
        MapLocation[] currentAllyFlagLocs = getCurrentAllyFlagLocations();
        MapLocation[] defaultAllyFlagLocs = getDefaultAllyFlagLocations();
        MapLocation[] closestEnemiesToFlags = { null, null, null };
        for (int i = nearbyEnemies.length; --i >= 0;) {
            MapLocation enemyLoc = nearbyEnemies[i].getLocation();
            for (int j = 3; --j >= 0;) {
                // use current flag loc if avail, otherwise default
                MapLocation currAlly = currentAllyFlagLocs[j];
                if (currAlly != null) {
                    if (closestEnemiesToFlags[j] == null || Pathfinder.travelDistance(currAlly, enemyLoc) < Pathfinder
                            .travelDistance(currAlly, closestEnemiesToFlags[j])) {
                        closestEnemiesToFlags[j] = enemyLoc;
                    }
                } else {
                    // default flags should be ok
                    MapLocation defaultAlly = defaultAllyFlagLocs[j];
                    if (closestEnemiesToFlags[j] == null || Pathfinder.travelDistance(defaultAlly,
                            enemyLoc) < Pathfinder.travelDistance(defaultAlly, closestEnemiesToFlags[j])) {
                        closestEnemiesToFlags[j] = enemyLoc;
                    }
                }
            }
        }
        // write these to comms if closer than whats in comms
        for (int i = 3; --i >= 0;) {
            MapLocation commClosestEnemy = decodeLoc(27 + i);
            MapLocation localClosestEnemy = closestEnemiesToFlags[i];
            if (localClosestEnemy == null)
                continue;
            MapLocation allyFlagLoc = currentAllyFlagLocs[i] == null ? defaultAllyFlagLocs[i] : currentAllyFlagLocs[i];
            if (commClosestEnemy == null || Pathfinder.travelDistance(allyFlagLoc, commClosestEnemy) > Pathfinder
                    .travelDistance(allyFlagLoc, localClosestEnemy)) {
                write(27 + i, encodeLoc(localClosestEnemy));
                refreshIdxs[++refreshPtr] = 27 + i;
            }
        }
    }

    static int encodeLoc(MapLocation loc) {
        // as x and y coords <= 63 and non neg they fit in 6 bits
        return (1 << 15) + (loc.x << 6) + (loc.y);
    }

    static MapLocation decodeLoc(int encoded) {
        if (encoded >> 15 == 1) {
            return new MapLocation(((encoded >> 6) & 0x3f), (encoded & 0x3f));
        } else {
            return null;
        }
    }
}
