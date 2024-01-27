package testbot;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;

enum Spec {
    ATT,
    BUILD,
    HEAL,
    NONE
}

// from
// https://www.geeksforgeeks.org/creating-a-user-defined-printable-pair-class-in-java/
// since im lazy
class Pair<S, T> {
    S first;
    T second;

    // constructor for assigning values
    Pair(S first, T second) {
        this.first = first;
        this.second = second;
    }

    // printing the pair class
    @Override
    public String toString() {
        return first.toString() + "," + second.toString();
    }
}

public class Comms {
    // comms[0:2][15] ally flag exists
    // comms[0:2][11:6], [5:0] x, y coords of default ally flag locs
    // comms[3:5][15] ally flag exists
    // comms[3:5][11:6], [5:0] x, y coords of current ally flag locs
    // comms[6:8] flag ids of ally flags, all ally flag info follows this order
    // ex: comms[0] will always be same flag as comms[3] and comms[6]

    // comms[9:11][15] enemy flag exists
    // comms[9:11][11:6], [5:0] x, y coords of default enemy flag locs
    // comms[12:14][15] enemy flag exists, [14] carry bit
    // comms[12:14][11:6], [5:0] x, y coords of current enemy flag locs
    // comms[15:17] flag ids of enemy flags, all enemy flag info follows this order
    // comms[18] ally attack spec ducks
    // comms[19] ally build spec ducks
    // comms[20] ally heal spec ducks
    // comms[21:24][15], [11:6], [5:0] exists, x, y of 4 randomly sampled enemies

    // comms[25:27][15][11:6][5:0] closest enemy loc to each ally flag(current if
    // exist, otherwise default)

    // comms[28:40] ally cd tracking, 2 bit action cd 2 bit move cd for 50 units
    // in order of shortID
    // 00 - nothing, 01 - 10, 10 - 20, 11 - 20+

    // comms[62] enemy alive count(overestimate)
    // comms[63] unit count for random sampling and id sequencing
    // these traps were set off between your last turn and your current turn, and so
    // could either be valid for 4 or 5 turns but for simplicity we assume 4

    public static final int[] ALLY_DEFAULT_FLAG_INDICES = { 0, 1, 2 };
    public static final int[] ALLY_CURRENT_FLAG_INDICES = { 3, 4, 5 };
    public static final int[] ENEMY_DEFAULT_FLAG_INDICES = { 9, 10, 11 };
    public static final int[] ENEMY_CURRENT_FLAG_INDICES = { 12, 13, 14 };
    public static final int[] STUN_TRAP_INDICES = { 30, 31, 32, 33 };
    public static final int ENEMY_COUNT_INDEX = 62;
    public static final int SEQUENCE_INDEX = 63;
    public static final int CARRY_BIT = 14;

    public static RobotController rc;
    public static int[] comms = new int[64];
    public static int shortId;

    public static RobotInfo[] nearbyEnemies;
    public static FlagInfo[] nearbyFlags;
    public static int carry_idx = -1;
    public static Spec prevSpec = Spec.NONE;
    // killed enemy unit respawn tracking
    public static int turnKillCount = 0;
    public static LinkedList<Pair<Integer, Integer>> respawnTimer = new LinkedList<>();
    public static MapLocation prevEndTurnLoc = null;

    // comms indices you are in charge of refreshing
    public static int[] refreshIdxs = new int[16];
    public static int[] prevVals = new int[16];
    public static int refreshPtr = -1;

    // absolute turn order robotIDs
    public static int[] turnOrder = new int[50];
    public static HashMap<Integer, Integer> idToShortId = new HashMap<>();

    // dropped enemy flag turn and loc for clearing if it gets returned
    public static int droppedEnemyFlagTurn = -1;
    public static MapLocation droppedEnemyFlagLoc = null;
    public static int droppedEnemyFlagIdx = -1;

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
            comms[index] = val; // update locally
        }
    }

    // sequence unit ids(0-49) assumes starts at 0
    public static void sequence() throws GameActionException {
        shortId = comms[SEQUENCE_INDEX];
        write(SEQUENCE_INDEX, shortId + 1);
    }

    // init comm values updated by first unit
    public static void initialize() throws GameActionException {
        // this should be called round 1 before update and so first unit sees 0(before
        // sequence)
        sequence();
        if (shortId == 0) {
            write(ENEMY_COUNT_INDEX, 50);
        }
        // absolute turn order noting
        // uses comm indices 12 through 61
        for (int i = 0; i < shortId; i++) {
            turnOrder[i] = comms[i + 12];
        }
        // write own id
        turnOrder[shortId] = rc.getID();
        write(shortId + 12, rc.getID());
    }

    // finish getting turn order next turn
    public static void init2() throws GameActionException {
        for (int i = shortId + 1; i < 50; i++) {
            turnOrder[i] = comms[i + 12];
        }
        // clear all these indices if you are last unit
        if (shortId == 49) {
            for (int i = 0; i < 50; i++) {
                write(i + 12, 0);
            }
        }
        // populate id to short id mapping
        for (int i = 50; --i >= 0;) {
            idToShortId.put(turnOrder[i], i);
        }
    }

    public static void update() throws GameActionException {
        // these can be called regardless of dead or alive
        if (rc.getRoundNum() > 1) {
            sequence();
        }

        // refresh the indices you are in charge of(enemy locs mostly)
        // logic for this instead of end of turn clear is so that units that execute
        // code first in turn(shortid = 1) have sufficient info
        for (; refreshPtr >= 0; refreshPtr--) {
            int idx = refreshIdxs[refreshPtr];
            if (comms[idx] == prevVals[refreshPtr])
                write(idx, 0);
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
                    write(18, comms[18] - 1);
                    break;
                case BUILD:
                    write(19, comms[19] - 1);
                    break;
                case HEAL:
                    write(20, comms[20] - 1);
                    break;
                case NONE:
                    break;
            }
            switch (curSpec) {
                case ATT:
                    write(18, comms[18] + 1);
                    break;
                case BUILD:
                    write(19, comms[19] + 1);
                    break;
                case HEAL:
                    write(20, comms[20] + 1);
                    break;
                case NONE:
                    break;
            }
        }

        // do we have the flag drop upgrade at this point?
        GlobalUpgrade[] upgrades = rc.getGlobalUpgrades(rc.getTeam());
        boolean hasFlagDrop = false;
        for (int i = upgrades.length; --i >= 0;) {
            if (upgrades[i] == GlobalUpgrade.CAPTURING) {
                hasFlagDrop = true;
                break;
            }
        }

        // if you have dropped a flag and that flag current pos has changed in
        // comms(update) you don't need to clear it later(someone else will handle it)
        int flagStickTurns = hasFlagDrop ? 4 + GlobalUpgrade.CAPTURING.flagReturnDelayChange : 4;
        if (droppedEnemyFlagIdx != -1 && comms[12 + droppedEnemyFlagIdx] != encodeLoc(droppedEnemyFlagLoc)) {
            droppedEnemyFlagTurn = -1;
            droppedEnemyFlagLoc = null;
            droppedEnemyFlagIdx = -1;
        } else if (droppedEnemyFlagIdx != -1 && rc.getRoundNum() - droppedEnemyFlagTurn > flagStickTurns) {
            // clear dropped flag if it has been past stick time
            MapLocation[] defaultEnemyFlagLocs = getDefaultEnemyFlagLocations();
            write(12 + droppedEnemyFlagIdx, encodeLoc(defaultEnemyFlagLocs[droppedEnemyFlagIdx]));
            droppedEnemyFlagTurn = -1;
            droppedEnemyFlagLoc = null;
            droppedEnemyFlagIdx = -1;
        }

        // sense updating for alive guys only
        if (rc.isSpawned()) {
            // we dropped the flag this turn(this runs end of each turn and we had flag at
            // end of last)
            nearbyFlags = rc.senseNearbyFlags(-1);
            if (carry_idx != -1 && !rc.hasFlag()) {
                droppedEnemyFlagTurn = rc.getRoundNum();
                // look for where we dropped flag within 2 r^2
                int droppedFlagID = comms[15 + carry_idx];
                for (int i = nearbyFlags.length; --i >= 0;) {
                    if (nearbyFlags[i].getID() == droppedFlagID) {
                        droppedEnemyFlagLoc = nearbyFlags[i].getLocation();
                        break;
                    }
                }
                droppedEnemyFlagIdx = carry_idx;
                // if we cant find it we captured it
                if (droppedEnemyFlagLoc == null) {
                    droppedEnemyFlagTurn = -1;
                    droppedEnemyFlagIdx = -1;
                }
                carry_idx = -1;
            }
            updateFlagLocs();
            if (rc.getRoundNum() > 2) {
                nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                sampleRandomEnemies();
                updateCurrFlags();
                updateClosestEnemyToAllyFlags();
                writeSelfCDs();
            }
        } else {
            // if dead clear carry bit from flag you were carrying and note dropped flag
            if (carry_idx != -1) {
                write(12 + carry_idx, comms[12 + carry_idx] & ~(1 << CARRY_BIT));
                droppedEnemyFlagTurn = rc.getRoundNum() - 1;
                droppedEnemyFlagLoc = prevEndTurnLoc;
                droppedEnemyFlagIdx = carry_idx;
                carry_idx = -1;
            }
        }
        prevEndTurnLoc = rc.isSpawned() ? rc.getLocation() : null;

        // kill count
        if (turnKillCount > 0) {
            respawnTimer.add(new Pair<>(rc.getRoundNum() + GameConstants.JAILED_ROUNDS, turnKillCount));
        }
        // update global enemy estimate
        int delta = -turnKillCount;
        // go through respawn q, for prev killed units that could have respawned add
        // them back to global q
        while (respawnTimer.size() > 0) {
            Pair<Integer, Integer> nextRespawn = respawnTimer.peek();
            if (nextRespawn.first <= rc.getRoundNum()) {
                respawnTimer.remove();
                delta += nextRespawn.second;
            } else {
                break;
            }
        }
        write(ENEMY_COUNT_INDEX, comms[ENEMY_COUNT_INDEX] + delta);
        turnKillCount = 0;
        prevSpec = curSpec;

        // clear comms if last unit for next turn
        if (shortId == 49) {
            // clear unit spec, sequence
            write(SEQUENCE_INDEX, 0);
        }

    }

    public static int getAllyAttackSpecs() {
        return comms[18];
    }

    public static int getAllyBuildSpecs() {
        return comms[19];
    }

    public static int getAllyHealSpecs() {
        return comms[20];
    }

    public static int getEnemyCount() throws GameActionException {
        return comms[ENEMY_COUNT_INDEX];
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
    static int writeToFirstAvail(MapLocation loc, int[] indices) throws GameActionException {
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
            if (comms[i + 6] == flagID || comms[i + 15] == flagID)
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
                        int firstFree = writeToFirstAvail(fi.getLocation(), ALLY_DEFAULT_FLAG_INDICES);
                        write(6 + firstFree, fi.getID());
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
                if (!fi.isPickedUp() && rc.getRoundNum() > 2) {
                    int idx = getFlagIndexFromID(fi.getID());
                    if (idx == -1) {
                        // new default enemy flag found! naisu!
                        int firstFree = writeToFirstAvail(fi.getLocation(), ENEMY_DEFAULT_FLAG_INDICES);
                        idx = firstFree - 9;
                        write(15 + idx, fi.getID());
                    }
                    write(12 + idx, encodeLoc(fi.getLocation()));
                }
            }

            // if carrying enemy flag continuously refresh ping
            if (rc.getRoundNum() > 200 && rc.hasFlag() && fi.getLocation().equals(rc.getLocation())) {
                if (fi.getTeam() != rc.getTeam()) {
                    // enemy current pings persistent after death to avoid default dup(check
                    // currents)
                    int idx = getFlagIndexFromID(fi.getID());
                    carry_idx = idx;
                    if (idx != -1) {
                        write(12 + idx, encodeLoc(fi.getLocation()) + (1 << CARRY_BIT));
                    }
                }
            }
        }
    }

    // update curr flags by invalidating dissapeared flags
    public static void updateCurrFlags() throws GameActionException {
        MapLocation[] defaultEnemyFlagLocs = getDefaultEnemyFlagLocations();
        for (int i = 3; --i >= 0;) {
            // enemy flags
            MapLocation loc = decodeLoc(comms[12 + i]);
            // can only invalidate comm if you can see the loc
            if (loc == null || loc.distanceSquaredTo(rc.getLocation()) > GameConstants.VISION_RADIUS_SQUARED)
                continue;
            boolean validComm = false;
            for (int j = nearbyFlags.length; --j >= 0;) {
                if (nearbyFlags[j].getLocation().equals(loc)) {
                    validComm = true;
                }
            }
            if (!validComm) {
                // if its not here anymore and no other ally has it it must have returned to its
                // default loc
                write(12 + i, encodeLoc(defaultEnemyFlagLocs[i]));
            }
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
        return new MapLocation[] { decodeLoc(comms[9]), decodeLoc(comms[10]), decodeLoc(comms[11]) };
    }

    // can have null entries
    public static MapLocation[] getCurrentEnemyFlagLocations() {
        return new MapLocation[] { decodeLoc(comms[12]), decodeLoc(comms[13]), decodeLoc(comms[14]) };
    }

    public static boolean[] getCarriedEnemyFlags() {
        return new boolean[] { ((comms[12] >> CARRY_BIT) & 0x1) == 1, ((comms[13] >> CARRY_BIT) & 0x1) == 1,
                ((comms[14] >> CARRY_BIT) & 0x1) == 1 };
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
                MapLocation currSight = decodeLoc(comms[21 + i]);
                // TODO now that enemy sighting clear is controlled by writer idt this pr calc
                // is correct
                if (currSight == null || (RobotPlayer.rng.nextDouble() < 1.f / 50)) {
                    int toWrite = encodeLoc(r.getLocation());
                    write(21 + i, toWrite);
                    refreshIdxs[++refreshPtr] = 21 + i;
                    prevVals[refreshPtr] = toWrite;
                    break;
                }
            }
        }
    }

    // returns arr of len 4 of sampled enemies, can contain null entries
    public static MapLocation[] getSampledEnemies() throws GameActionException {
        MapLocation[] enemies = new MapLocation[4];
        for (int i = 4; --i >= 0;) {
            enemies[i] = decodeLoc(comms[21 + i]);
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
        return new MapLocation[] { decodeLoc(comms[25]), decodeLoc(comms[26]), decodeLoc(comms[27]) };
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
            MapLocation commClosestEnemy = decodeLoc(comms[25 + i]);
            MapLocation localClosestEnemy = closestEnemiesToFlags[i];
            if (localClosestEnemy == null)
                continue;
            MapLocation allyFlagLoc = currentAllyFlagLocs[i] == null ? defaultAllyFlagLocs[i] : currentAllyFlagLocs[i];
            if (commClosestEnemy == null || Pathfinder.travelDistance(allyFlagLoc, commClosestEnemy) > Pathfinder
                    .travelDistance(allyFlagLoc, localClosestEnemy)) {
                int toWrite = encodeLoc(localClosestEnemy);
                write(25 + i, toWrite);
                refreshIdxs[++refreshPtr] = 25 + i;
                prevVals[refreshPtr] = toWrite;
            }
        }
    }

    // cd returned is either 0, 10, 20, or 30(anything over 20)
    // cd is rounded down to nearest 10
    // returns [action, move] cds of ally
    public static int[] getAllyCDs(int tgtID) {
        int allyShortId = idToShortId.get(tgtID);
        int[] cds = new int[2];
        int comm_idx = 28 + allyShortId / 4;
        // offset from right, this is basically big endian
        int comm_offset = 4 * (3 - (allyShortId % 4));
        int cd_bits = comms[comm_idx] >> comm_offset;
        cds[0] = 10 * ((cd_bits >> 2) & 0x3);
        cds[1] = 10 * (cd_bits & 0x3);
        return cds;
    }

    public static void writeSelfCDs() throws GameActionException {
        int comm_idx = 28 + shortId / 4;
        // offset from right, this is basically big endian
        int comm_offset = 4 * (3 - (shortId % 4));
        // 4 cd bits
        int action_cd = Math.min(rc.getActionCooldownTurns(), 30) / 10;
        int move_cd = Math.min(rc.getMovementCooldownTurns(), 30) / 10;
        // bmask to mask out old self cd vals, bit manip
        int bmask = ~(0xf << comm_offset);
        int fcomm_val = (bmask & comms[comm_idx]) | ((action_cd << 2 | move_cd) << comm_offset);
        write(comm_idx, fcomm_val);
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

    // Parameters: 2 robot IDs
    // Returns: true if the first robot goes before the second robot in the turn
    static boolean turnOrderBefore(int id1, int id2) {
        return idToShortId.get(id1) < idToShortId.get(id2);
    }
}
