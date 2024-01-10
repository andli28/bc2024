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
    // comms[17:19][15], [11:6], [5:0] exists, x, y of closest enemy to each ally
    // flag(current loc)
    // comms[20] unit count for random sampling and id sequencing

    public static RobotController rc;
    public static int[] comms = new int[64];
    public static int shortId;

    public static RobotInfo[] nearbyEnemies;

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
        // reformat to extract this
        // optimally 1 call pre and post move
        nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        receive();
        sequence();

        sampleRandomEnemies();

        // clear comms if last unit for next turn
        if (shortId == 49) {
            write(20, 0);
            clearRandomEnemies();
        }
    }

    // TODO uuh how to know if default or carried? how often refresh?
    public static void updateFlagLocs() throws GameActionException {
        FlagInfo[] nearbyFlags = rc.senseNearbyFlags(-1);
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
                MapLocation currSight = decodeLoc(Comms.comms[13 + i]);
                if (currSight == null || (RobotPlayer.rng.nextDouble() < 1.f / (Comms.comms[20]))) {
                    write(13 + i, encodeLoc(r.getLocation()));
                    break;
                }
            }
        }
    }

    public static MapLocation getClosestSampleEnemy() {
        MapLocation closestSampleEnemyLoc = null;
        for (int i = 4; --i >= 0;) {
            if (comms[13 + i] != 0) {
                MapLocation enemy = decodeLoc(comms[13 + i]);
                if (closestSampleEnemyLoc == null
                        || (closestSampleEnemyLoc.distanceSquaredTo(rc.getLocation()) > enemy
                                .distanceSquaredTo(rc.getLocation()))) {
                    closestSampleEnemyLoc = enemy;
                }
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
