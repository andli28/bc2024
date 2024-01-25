package v7;

import battlecode.common.*;

public class Info {
    public static RobotController rc;
	public static int round_num;
    public static Team team;
    public static Team enemyTeam;
    public static int MAP_HEIGHT;
    public static int MAP_WIDTH;
    public static int VISION_DIST;
    
    public static MapLocation[] spawnLocs;
    public static MapLocation closestSpawn;
    public static int numFlagsNearbyNotPickedUp;
    public static MapLocation closestFlag;
    public static int closestFlagDist;

    public static RobotInfo[] friendly_robots;    
    public static MapLocation centerOfFriendliesLocation;



    public static RobotInfo[] enemy_robots;


    public static void initialize(RobotController rc) throws GameActionException{
        Info.rc = rc;
        // TODO: Add other initialization code here (transfer some from RobotPlayer)

        spawnLocs = rc.getAllySpawnLocations();

    }

    public static void update() throws GameActionException{
        friendly_robots = rc.senseNearbyRobots(-1, rc.getTeam());

        // find closest spawnLoc:
        closestSpawn = null;
        int closestSpawnDist = Integer.MAX_VALUE;
        for (int i = spawnLocs.length - 1; i >= 0; i--) {
            int distSqToSpawn = Pathfinder.travelDistance(rc.getLocation(), spawnLocs[i]);
            if (distSqToSpawn < closestSpawnDist) {
                closestSpawnDist = distSqToSpawn;
                closestSpawn = spawnLocs[i];
            }
        }

        // Flag Counting, finding number of nearby flags not picked up
        FlagInfo[] nearbyFlags = rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED,
                rc.getTeam().opponent());
        int numFlagsNearbyNotPickedUp = 0;
        if (nearbyFlags.length != 0) {
            for (int i = nearbyFlags.length - 1; i >= 0; i--) {
                if (!nearbyFlags[i].isPickedUp()) {
                    numFlagsNearbyNotPickedUp++;
                }
            }
        }
        Info.numFlagsNearbyNotPickedUp = numFlagsNearbyNotPickedUp;

        // find closest flagLoc:
        MapLocation closestFlag = null;
        int closestFlagDist = Integer.MAX_VALUE;
        for (int i = nearbyFlags.length - 1; i >= 0; i--) {
            if (!nearbyFlags[i].isPickedUp()) {
                int distSqToSpawn = rc.getLocation()
                        .distanceSquaredTo(nearbyFlags[i].getLocation());
                if (distSqToSpawn < closestFlagDist) {
                    closestFlagDist = distSqToSpawn;
                    closestFlag = nearbyFlags[i].getLocation();
                }
            }
        }
        Info.closestFlag = closestFlag;
        Info.closestFlagDist = closestFlagDist;

    }

}
