package mainbot;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;

public class Comms {
    // comms[0:2][15] ally flag exists
    // comms[0:2][11:6], [5:0] x, y coords of default ally flag locs
    // comms[3:5][15] ally flag exists
    // comms[3:5][11:6], [5:0] x, y coords of current ally flag locs
    // comms[6:8][15] enemy flag exists
    // comms[6:8][11:6], [5:0] x, y coords of default enemy flag locs
    // comms[9:11][15] enemy flag exists
    // comms[9:11][11:6], [5:0] x, y coords of current enemy flag locs
    // comms[12][11:6], [5:0] counts of attack, build spec ducks
    // comms[12][15:10] count of heal spec ducks
    // comms[13:16][15], [11:6], [5:0] exists, x, y of 4 randomly sampled enemies
    // comms[17:19][15], [14], [11:6], [5:0] exists, picked up, x, y of closest
    // enemy to each ally
    // flag(current loc)
    // comms[20] unit count for random sampling and id sequencing
    // comms[21] enemy alive count

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
    public static int refreshIdx = -1;

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
        shortId = comms[20];
        write(20, shortId + 1);
    }

    public static void update() throws GameActionException {
        // these can be called regardless of dead or alive
        receive();
        sequence();
        if (refreshIdx != -1) {
            write(refreshIdx, 0);
            refreshIdx = -1;
        }

        // sense updating for alive guys only
        if (rc.isSpawned()) {
            // reformat to extract this
            // optimally 1 call pre and post move
            nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            sampleRandomEnemies();
            nearbyFlags = rc.senseNearbyFlags(-1);
            updateFlagLocs();
        }

        // clear comms if last unit for next turn
        if (shortId == 49) {
            write(20, 0);
            clearRandomEnemies();
        }
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

    // call this whenever a flag is picked up for comms updating
    // in setup, need to remove default ally flag loc in comms
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
        } else {
            // TODO some commed dropped flag accounting to prevent dups?
        }
        carrying = Team.NEUTRAL;
    }

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
                        refreshIdx = idx;
                    }
                }
            } else {
                // default/current enemy flag loc

                // FIXME possible issue where dropped enemy flags get counted as default and we
                // dup a flag into persistent mem
                // solve by also recording dropped pos?
                if (!fi.isPickedUp()) {
                    writeFlagLoc(fi.getLocation(), ENEMY_DEFAULT_FLAG_INDICES);
                    writeFlagLoc(fi.getLocation(), ENEMY_CURRENT_FLAG_INDICES);
                }
            }

            // if carrying a flag remove previous ping and update current flag
            if (carrying == fi.getTeam() && fi.getLocation().equals(rc.getLocation())) {
                if (fi.getTeam() != rc.getTeam()) {
                    // enemy current
                    writeFlagLoc(fi.getLocation(), ENEMY_CURRENT_FLAG_INDICES);
                }
            }
        }
    }

    public static MapLocation[] getDefaultAllyFlagLocations() {
        return new MapLocation[] { decodeLoc(comms[0]), decodeLoc(comms[1]), decodeLoc(comms[2]) };
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
        write(13, 0);
        write(14, 0);
        write(15, 0);
        write(16, 0);
    }

    public static void sampleRandomEnemies() throws GameActionException {
        if (nearbyEnemies.length > 0 && rc.canWriteSharedArray(63, 0)) {
            for (int i = 4; --i >= 0;) {
                RobotInfo r = nearbyEnemies[RobotPlayer.rng.nextInt(nearbyEnemies.length)];
                MapLocation currSight = decodeLoc(comms[13 + i]);
                if (currSight == null || (RobotPlayer.rng.nextDouble() < 1.f / (comms[20]))) {
                    write(13 + i, encodeLoc(r.getLocation()));
                    break;
                }
            }
        }
    }

    // returns arr of len 4 of sampled enemies, can contain null entries
    public static MapLocation[] getSampledEnemies() {
        MapLocation[] enemies = new MapLocation[4];
        for (int i = 4; --i >= 0;) {
            enemies[i] = decodeLoc(comms[13 + i]);
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
