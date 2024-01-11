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
    public static int[] refreshIdxs = new int[6];
    public static int refreshPtr = -1;
    public static Spec prevSpec = Spec.NONE;
    // killed enemy unit respawn tracking
    public static int turnKillCount = 0;
    public static LinkedList<Pair> respawnTimer = new LinkedList<>();

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
        // refresh the comms entries you are in charge of(1 turn lifespan)
        for (; refreshPtr >= 0; refreshPtr--) {
            // if you are dead do not refresh the enemy current flags
            if (!rc.isSpawned() && refreshIdxs[refreshPtr] >= 9 && refreshIdxs[refreshPtr] <= 11)
                continue;
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
            updateCurrEnemyFlags();
        }

        // kill count
        if (turnKillCount > 0) {
            respawnTimer.add(new Pair(rc.getRoundNum() + 25, turnKillCount));
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
            // clear unit spec, sequence, sampled enemy counts
            write(19, 0);
            clearRandomEnemies();
        }
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

    // FIXME dont rely on robotplayer call, handle these transitions here(only
    // relevant once we start picking up own flags)
    public static void flagPickup(FlagInfo finfo) throws GameActionException {
        // picking up own flag must be setup relocation
        if (finfo.getTeam() == rc.getTeam()) {
            // remove entry from default ally flag locs
            removeValue(encodeLoc(finfo.getLocation()), ALLY_DEFAULT_FLAG_INDICES);
        }
        carrying = finfo.getTeam();
    }

    public static void flagDrop(FlagInfo finfo) throws GameActionException {
        if (finfo.getTeam() == rc.getTeam()) {
            // update default ally with drop location in setup
            writeFlagLoc(finfo.getLocation(), ALLY_DEFAULT_FLAG_INDICES);
        }
        carrying = Team.NEUTRAL;
    }
    // END FIXME

    // default locs are persistent(except ally pickup + move in setup phase)
    // current locs clear every round
    public static void updateFlagLocs() throws GameActionException {
        for (int i = nearbyFlags.length; --i >= 0;) {
            FlagInfo fi = nearbyFlags[i];
            // setup phase default ally
            if (fi.getTeam() == rc.getTeam()) {
                if (rc.getRoundNum() < 200 && !fi.isPickedUp()) {
                    // default flag loc, record into comms if not there already
                    writeFlagLoc(fi.getLocation(), ALLY_DEFAULT_FLAG_INDICES);
                } else if (rc.getRoundNum() > 200) {
                    // current ally flag locs
                    int idx = writeFlagLoc(fi.getLocation(), ALLY_CURRENT_FLAG_INDICES);
                    // note idx for refresh next time it is this bots turn
                    if (idx != -1) {
                        refreshIdxs[++refreshPtr] = idx;
                    }
                }
            } else {
                // default/current enemy flag loc
                if (!fi.isPickedUp()) {
                    // check if this is a dropped flag before writing to default
                    MapLocation fl = fi.getLocation();
                    MapLocation[] currEnemyFlags = getCurrentEnemyFlagLocations();
                    if (!(fl.equals(currEnemyFlags[0]) || fl.equals(currEnemyFlags[1])
                            || fl.equals(currEnemyFlags[2]))) {
                        writeFlagLoc(fi.getLocation(), ENEMY_DEFAULT_FLAG_INDICES);
                    }
                }
            }

            // if carrying enemy flag remove previous ping and update current flag
            if (rc.getRoundNum() > 200 && rc.hasFlag() && fi.getLocation().equals(rc.getLocation())) {
                if (fi.getTeam() != rc.getTeam()) {
                    // enemy current pings persistent after death to avoid default dup(check
                    // currents)
                    int idx = writeFlagLoc(fi.getLocation(), ENEMY_CURRENT_FLAG_INDICES);
                    if (idx != -1) {
                        refreshIdxs[++refreshPtr] = idx;
                    }
                }
            }
        }
    }

    // if flag carrier killed the currentflag entry persists, after another duck
    // comes to verify this clears comm entry
    public static void updateCurrEnemyFlags() throws GameActionException {
        for (int i = 3; --i >= 0;) {
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
        for (int i = 3; --i >= 0;) {
            MapLocation loc = decodeLoc(comms[9 + i]);
            if (loc == null)
                continue;
            boolean displaced = true;
            for (int j = 3; --j >= 0;) {
                MapLocation defaultLoc = decodeLoc(comms[6 + j]);
                if (defaultLoc.equals(loc))
                    displaced = false;
            }
            if (displaced) {
                displacedEnemyFlags[i] = loc;
            }
        }
        return displacedEnemyFlags;
    }

    public static MapLocation closestDisplacedEnemyFlag() {
        MapLocation closest = null;
        int dist = 9999;
        MapLocation src = rc.getLocation();
        MapLocation[] displayedEnemyFlags = getDisplacedEnemyFlags();
        for (int i = 3; --i >= 0;) {
            MapLocation loc = displayedEnemyFlags[i];
            if (loc == null)
                continue;
            if (closest == null || dist > loc.distanceSquaredTo(src)) {
                closest = loc;
                dist = src.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    // returns arr of size 3 containing displaced ally flag current locations, can
    // contain null entries
    public static MapLocation[] getDisplacedAllyFlags() {
        MapLocation[] displacedAllyFlags = new MapLocation[] { null, null, null };
        for (int i = 3; --i >= 0;) {
            MapLocation loc = decodeLoc(comms[3 + i]);
            if (loc == null)
                continue;
            boolean displaced = true;
            for (int j = 3; --j >= 0;) {
                MapLocation defaultLoc = decodeLoc(comms[j]);
                if (defaultLoc.equals(loc))
                    displaced = false;
            }
            if (displaced) {
                displacedAllyFlags[i] = loc;
            }
        }
        return displacedAllyFlags;
    }

    public static MapLocation closestDisplacedAllyFlag() {
        MapLocation closest = null;
        int dist = 9999;
        MapLocation src = rc.getLocation();
        MapLocation[] displayedAllyFlags = getDisplacedAllyFlags();
        for (int i = 3; --i >= 0;) {
            MapLocation loc = displayedAllyFlags[i];
            if (loc == null)
                continue;
            if (closest == null || dist > loc.distanceSquaredTo(src)) {
                closest = loc;
                dist = src.distanceSquaredTo(loc);
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
                if (currSight == null || (RobotPlayer.rng.nextDouble() < 1.f / (comms[19]))) {
                    write(15 + i, encodeLoc(r.getLocation()));
                    break;
                }
            }
        }
    }

    // returns arr of len 4 of sampled enemies, can contain null entries
    public static MapLocation[] getSampledEnemies() {
        MapLocation[] enemies = new MapLocation[4];
        for (int i = 4; --i >= 0;) {
            enemies[i] = decodeLoc(comms[15 + i]);
        }
        return enemies;
    }

    public static MapLocation getClosestSampleEnemy() {
        MapLocation closestSampleEnemyLoc = null;
        MapLocation[] sampledEnemies = getSampledEnemies();
        for (int i = 4; --i >= 0;) {
            MapLocation enemy = sampledEnemies[i];
            if (closestSampleEnemyLoc == null
                    || (closestSampleEnemyLoc.distanceSquaredTo(rc.getLocation()) > enemy
                            .distanceSquaredTo(rc.getLocation()))) {
                closestSampleEnemyLoc = enemy;
            }
        }
        return closestSampleEnemyLoc;
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
