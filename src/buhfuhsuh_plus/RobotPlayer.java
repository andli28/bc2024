package buhfuhsuh_plus;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what
 * we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {
    public static RobotController rc;

    /**
     * We will use this variable to count the number of turns this robot has been
     * alive.
     * You can use static variables like this to save any information you want. Keep
     * in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between
     * your robots.
     */
    static int turnCount = 0;
    static int turnsAlive = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided
     * by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant
     * number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very
     * useful for debugging!
     */
    static final Random rng = new Random();

    /**
     * Array containing all the possible movement directions.
     */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    static final Direction[] allCombatDirs = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
    };

    // Designating Constants:
    static MapLocation tgtLocation = null;
    static int turnsNotReachedTgt = 0;
    static boolean haveSeenCombat = false;
    static boolean lastTurnPursingCrumb = false;
    static boolean lastTurnPursingWater = false;
    static MapLocation rememberThisTurnClosestStunTrap = null;
    static int turnsPursuingStun = 0;
    static boolean retireSentry = false;
    static MapLocation homeFlag = null;
    static int homeFlagIndex = -1;
    static HashSet<Integer> prevEnemiesIn1Step = new HashSet<>();
    static int prevHp = 1000;

    // Delegating Roles:
    // 1. Scouting - base role for units. Purpose: to explore the map, gather
    // information, and gather breadcrumbs.
    // 2. InCombat - role for when an enemy unit is seen. Purpose: win the
    // engagement, or decide to retreat.
    static final int SCOUTING = 0;
    static final int INCOMBAT = 1;
    static final int BUILDING = 2;
    static final int CAPTURING = 3;
    static final int RETURNING = 4;
    static final int DEFENDING = 5;
    static final int HEALING = 6;
    static final int WOUNDED = 7;
    static final int SENTRYING = 8;
    static final int RESPAWN = 9;
    static final int CRUMBS = 10;
    static final int LINEUP = 11;
    static final int ESCORT = 12;
    static final int TRAINBUILD = 13;

    // Default unit to scouting.
    static int role = SCOUTING;

    // set specializations
    static boolean BUILDERSPECIALIST = false;
    static boolean HEALINGSPECIALIST = false;
    static boolean ATTACKSPECIALIST = false;
    static boolean SENTRY = false;
    static boolean sentryShiftOne = false;
    static boolean sentryShiftTwo = false;
    static boolean attackSquadOne = false;
    static boolean attackSquadTwo = false;
    static boolean attackSquadThree = false;
    static boolean initialSetTrapStun = false;
    static boolean initialSetTrapWater = false;
    static boolean shouldGoHomeAndTrap = false;

    // previous waypoints for backtracking on flag return
    static MapLocation[] prevWaypoints = new MapLocation[20];
    static int prevWaypointIndex = 0;
    static final int WAYPOINT_SPACING = 5; // min true travel dist between waypoints

    /**
     * run() is the method that is called when a robot is instantiated in the
     * Battlecode world.
     * It is like the main function for your robot. If this method returns, the
     * robot dies!
     *
     * @param rc The RobotController object. You use it to perform actions from this
     *           robot, and to get
     *           information on its current status. Essentially your portal to
     *           interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you
        // run a match!
        // System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        // rc.setIndicatorString("Hello world!");

        Pathfinder.rc = rc;
        Comms.rc = rc;
        RobotPlayer.rc = rc;
        Bfs.rc = rc;

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in
            // an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At
            // the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to
            // do.

            turnCount += 1; // We have now been alive for one more turn!

            // Resignation at 500 turns for testing purposes
            // if (turnCount == 200) {
            // rc.resign();
            // }

            // Try/catch blocks stop unhandled exceptions, which cause your robot to
            // explode.
            try {
                Comms.receive();
                if (rc.getRoundNum() == 1) {
                    Info.initialize(rc);
                    Comms.initialize();
                } else if (rc.getRoundNum() == 2) {
                    Comms.init2();
                }
                // Default battlecode code for spawning:
                // Make sure you spawn your robot in before you attempt to take any actions!
                // Robots not spawned in do not have vision of any tiles and cannot perform any
                // actions.
                if (!rc.isSpawned()) {
                    turnsAlive = 0;
                    haveSeenCombat = false;

                    // decide if this person should be a builder (if shortId == an ID) (Diff ID's
                    // chosen to spawn at diff spawns)
                    if (turnCount == 1) {
                        if (Comms.shortId == 2 || Comms.shortId == 29 || Comms.shortId == 47) {
                            BUILDERSPECIALIST = true;
                        }

                        // decide if this person should be a sentry (Diff ID's chosen to spawn at diff
                        // spawns)
                        // Create two shifts to swap out ducks and ensure these ducks can still get XP.
                        // Rotate shift every 500 turns.
                        sentryShiftOne = (Comms.shortId == 0 || Comms.shortId == 30 || Comms.shortId == 49);
                        sentryShiftTwo = (Comms.shortId == 1 || Comms.shortId == 31 || Comms.shortId == 48);

                        if (sentryShiftOne) {
                            SENTRY = true;
                        }

                        attackSquadOne = (Comms.shortId < 16);
                        attackSquadTwo = (Comms.shortId >= 16 && Comms.shortId < 32);
                        attackSquadThree = (Comms.shortId >= 32);
                        // 4 attack specialists per squad
                        ATTACKSPECIALIST = ((Comms.shortId > 1 && Comms.shortId < 6)
                                || (Comms.shortId > 15 && Comms.shortId < 20)
                                || (Comms.shortId > 31 && Comms.shortId < 36));
                    }
                    // Builder's initial spawn should be their home so that they have real estate to
                    // train
                    MapLocation initialBuilderSpawn = null;
                    // ensure sentries and builderspecialsits spawn at their appropriate spawns
                    if (turnCount == 2) {
                        MapLocation[] homeFlags = Comms.getDefaultAllyFlagLocations();
                        if (Comms.shortId == 0 || Comms.shortId == 1 || Comms.shortId == 2) {
                            homeFlag = homeFlags[0];
                            homeFlagIndex = 0;
                        } else if (Comms.shortId == 30 || Comms.shortId == 31 || Comms.shortId == 29) {
                            homeFlag = homeFlags[1];
                            homeFlagIndex = 1;
                        } else if (Comms.shortId == 48 || Comms.shortId == 49 || Comms.shortId == 47) {
                            homeFlag = homeFlags[2];
                            homeFlagIndex = 2;
                        }
                        if (BUILDERSPECIALIST) {
                            initialBuilderSpawn = homeFlag;
                        }
                    }

                    MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                    // If theres a displaced flag, find the spawn location
                    // that has the smallest distance to the first displaced flag in the list and
                    // spawn there
                    // Else if there is a randomly sampled enemy, find the spawn location
                    // flag pair that has the smallest distance to the first sampled enemy in the
                    // list and spawn there
                    // Spawn anywhere you can for now(spawns 50 in turn 1)

                    // finding if there is a displaced flag
                    MapLocation[] displacedFlags = Comms.getDisplacedAllyFlags();
                    MapLocation displacedFlag = null;
                    for (int i = displacedFlags.length - 1; i >= 0; i--) {
                        if (displacedFlags[i] != null) {
                            displacedFlag = displacedFlags[i];
                            break;
                        }
                    }

                    // finding the closest sampled enemy
                    MapLocation[] sampledEnemies = Comms.getSampledEnemies();
                    MapLocation sampledEnemy = null;
                    for (int i = sampledEnemies.length - 1; i >= 0; i--) {
                        if (sampledEnemies[i] != null) {
                            sampledEnemy = sampledEnemies[i];
                            break;
                        }
                    }

                    // finding the spawn/flag that is in danger with closest enemies.
                    MapLocation closestSpawnInDanger = null;
                    int enemyDistToClosestSpawnInDanger = Integer.MAX_VALUE;
                    MapLocation[] closestEnemiesToFlags = Comms.getClosestEnemyToAllyFlags();
                    int[] distsClosestEnemiesToFlags = Comms.getClosestEnemyDistanceToAllyFlags();
                    for (int i = closestEnemiesToFlags.length - 1; i >= 0; i--) {
                        if (closestEnemiesToFlags[i] != null
                                && distsClosestEnemiesToFlags[i] < enemyDistToClosestSpawnInDanger) {
                            closestSpawnInDanger = Comms.getCurrentAllyFlagLocations()[i];
                            enemyDistToClosestSpawnInDanger = distsClosestEnemiesToFlags[i];
                        }
                    }

                    // spawn location for sentries must be their home
                    MapLocation sentrySpawn = null;
                    if (SENTRY && !retireSentry) {
                        sentrySpawn = homeFlag;
                    }

                    // Spwwn at the closest location to a given MapLocation:
                    MapLocation targetSpawnClosestTo = null;
                    if (initialBuilderSpawn != null) {
                        targetSpawnClosestTo = initialBuilderSpawn;
                    } else if (sentrySpawn != null) {
                        targetSpawnClosestTo = sentrySpawn;
                    } else if (displacedFlag != null) {
                        targetSpawnClosestTo = displacedFlag;
                    } else if (closestSpawnInDanger != null) {
                        targetSpawnClosestTo = closestSpawnInDanger;
                    } else if (sampledEnemy != null) {
                        targetSpawnClosestTo = sampledEnemy;
                    }

                    // don't spawn specialists on turn one becuase they won't have a home. spawn
                    // everyone else equally amongst the spawns
                    if (!(turnCount == 1 && (sentryShiftOne || sentryShiftTwo || BUILDERSPECIALIST))) {
                        if (targetSpawnClosestTo != null) {
                            int distToTarget = Integer.MAX_VALUE;
                            MapLocation closestToTarget = null;
                            for (int i = spawnLocs.length - 1; i >= 0; i--) {
                                if (rc.canSpawn(spawnLocs[i])
                                        && spawnLocs[i].distanceSquaredTo(targetSpawnClosestTo) < distToTarget) {
                                    distToTarget = spawnLocs[i].distanceSquaredTo(targetSpawnClosestTo);
                                    closestToTarget = spawnLocs[i];
                                }
                            }
                            if (closestToTarget != null && rc.canSpawn(closestToTarget)) {
                                rc.spawn(closestToTarget);
                            }
                        } else {

                            MapLocation loc = spawnLocs[Comms.shortId % spawnLocs.length];
                            int distToTarget = Integer.MAX_VALUE;
                            MapLocation closestToTarget = null;
                            for (int i = spawnLocs.length - 1; i >= 0; i--) {
                                if (rc.canSpawn(spawnLocs[i])
                                        && spawnLocs[i].distanceSquaredTo(loc) < distToTarget) {
                                    distToTarget = spawnLocs[i].distanceSquaredTo(loc);
                                    closestToTarget = spawnLocs[i];
                                }
                            }
                            if (closestToTarget != null && rc.canSpawn(closestToTarget)) {
                                rc.spawn(closestToTarget);
                            }
                        }
                    }
                }

                if (rc.isSpawned()) {
                    // if (turnCount > 5 && Comms.shortId == 0) {
                    // System.out.println(turnCount);
                    // }
                    Info.update();
                    // if (turnCount > 2 && Comms.shortId ==1) {
                    // System.out.println(turnCount);
                    // }

                    // clear waypoint mem on spawn
                    if (turnsAlive == 0) {
                        prevWaypoints = new MapLocation[20];
                        prevWaypointIndex = 0;
                    }

                    // Turns Alive
                    turnsAlive++;

                    // Create two shifts to swap out ducks and ensure these ducks can still get XP.
                    // Rotate shift every 500 turns.
                    boolean swapTurnOne = ((turnCount >= 2 && turnCount < 500)
                            || (turnCount > 1000 && turnCount < 1500));
                    boolean swapTurnTwo = ((turnCount > 500 && turnCount < 1000)
                            || (turnCount > 1500 && turnCount < 2000));
                    boolean shiftTwoSwapIn = ((turnCount > 475 && turnCount < 1000)
                            || (turnCount > 1475 && turnCount < 2000));
                    boolean shiftOneSwapIn = (turnCount > 975 && turnCount < 1500);

                    if (swapTurnOne && sentryShiftOne) {
                        SENTRY = true;
                    } else if (swapTurnOne && sentryShiftTwo) {
                        SENTRY = false;
                    } else if (swapTurnTwo && sentryShiftTwo) {
                        SENTRY = true;
                    } else if (swapTurnTwo && sentryShiftOne) {
                        SENTRY = false;
                    }

                    // if you are a sentry, and it is your shift, prepare to sentry by respawning.
                    // TODO: allow sentry to simply run back home if close enough (can now be
                    // dragged away if chasing a target who has the flag)
                    boolean shouldRespawn = false;
                    if ((sentryShiftOne && shiftOneSwapIn || sentryShiftTwo && shiftTwoSwapIn)
                            && !rc.canSenseLocation(homeFlag) && !retireSentry) {
                        shouldRespawn = true;
                    }

                    // Attack -> Healing -> Capturing
                    if (turnCount == GameConstants.GLOBAL_UPGRADE_ROUNDS && rc.canBuyGlobal(GlobalUpgrade.ATTACK)) {
                        rc.buyGlobal(GlobalUpgrade.ATTACK);
                    } else if (turnCount == 2 * GameConstants.GLOBAL_UPGRADE_ROUNDS
                            && rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
                        rc.buyGlobal(GlobalUpgrade.HEALING);
                    } else if (turnCount == 3 * GameConstants.GLOBAL_UPGRADE_ROUNDS
                            && rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) {
                        rc.buyGlobal(GlobalUpgrade.CAPTURING);
                    }

                    // Information about the closest displaced flag + distance to that MapLocation
                    MapLocation closestDisplacedFlag = Comms.closestDisplacedAllyFlag();

                    MapLocation lastTurnClosestStunTrap = rememberThisTurnClosestStunTrap;
                    // MapInfo Counting: Counting Traps, Water, etc
                    MapLocation nearestStunTrap = null;
                    MapLocation nearestExplosiveTrap = null;
                    MapLocation nearestWaterTrap = null;
                    MapLocation nearestWater = null;
                    MapLocation nearestDividerWithOpenNeighbor = null;
                    MapLocation diggable = null;

                    int lowestDistToStunTrap = Integer.MAX_VALUE;
                    int lowestDistToExplosiveTrap = Integer.MAX_VALUE;
                    int lowestDistToWaterTrap = Integer.MAX_VALUE;
                    int lowestDistToWater = Integer.MAX_VALUE;
                    int lowestDistToDividerWithOpenNeighbor = Integer.MAX_VALUE;
                    int lowestDistToDam = Integer.MAX_VALUE;

                    MapInfo[] nearbyMap = rc.senseNearbyMapInfos();

                    for (int i = nearbyMap.length - 1; i >= 0; i--) {
                        MapInfo singleMap = nearbyMap[i];
                        int distToSingleMap = rc.getLocation().distanceSquaredTo(singleMap.getMapLocation());
                        if (BUILDERSPECIALIST && distToSingleMap <= 11 && singleMap.isPassable()
                                && !singleMap.isSpawnZone()
                                && !rc.isLocationOccupied(singleMap.getMapLocation())
                                && diggable == null && rc.getExperience(SkillType.BUILD) <= 30
                                && !doSidesHaveWater(rc, singleMap.getMapLocation())
                                && !aNeighborIsADamOrWall(rc, singleMap.getMapLocation())) {
                            diggable = singleMap.getMapLocation();
                        }
                        if (singleMap.isWater() && distToSingleMap < lowestDistToWater) {
                            lowestDistToWater = distToSingleMap;
                            nearestWater = singleMap.getMapLocation();
                        }
                        if (singleMap.getTrapType() == TrapType.EXPLOSIVE
                                && distToSingleMap < lowestDistToExplosiveTrap) {
                            lowestDistToExplosiveTrap = distToSingleMap;
                            nearestExplosiveTrap = singleMap.getMapLocation();
                        } else if (singleMap.getTrapType() == TrapType.STUN && distToSingleMap < lowestDistToStunTrap) {
                            lowestDistToStunTrap = distToSingleMap;
                            nearestStunTrap = singleMap.getMapLocation();
                        } else if (singleMap.getTrapType() == TrapType.WATER
                                && distToSingleMap < lowestDistToWaterTrap) {
                            lowestDistToWaterTrap = distToSingleMap;
                            nearestWaterTrap = singleMap.getMapLocation();
                        }
                        if (singleMap.isDam() && !singleMap.isWall()) {
                            if (distToSingleMap < lowestDistToDam) {
                                lowestDistToDam = distToSingleMap;
                            }
                            if (distToSingleMap < lowestDistToDividerWithOpenNeighbor
                                    && areNeighborsOpen(rc, singleMap.getMapLocation())) {
                                lowestDistToDividerWithOpenNeighbor = distToSingleMap;
                                nearestDividerWithOpenNeighbor = singleMap.getMapLocation();
                            }
                        }
                    }
                    rememberThisTurnClosestStunTrap = nearestStunTrap;

                    // if you were randomly chosen as a builder specialist && turncount is between
                    // 75 and max setup rounds
                    // , train by digging until you have 30 exp

                    // Splitting units into groups that equally attack all flags. If it exists in
                    // comms,
                    // set target as that, otherwise use broadcase
                    MapLocation targetFlag = null;
                    MapLocation[] enemyFlagLocs = Comms.getCurrentEnemyFlagLocations();
                    MapLocation[] broadCastLocs = rc.senseBroadcastFlagLocations();
                    boolean[] targetsCarried = Comms.getCarriedEnemyFlags();

                    if (attackSquadOne) {
                        if (enemyFlagLocs[0] != null && !targetsCarried[0]) {
                            targetFlag = enemyFlagLocs[0];
                        } else if (enemyFlagLocs[1] != null && !targetsCarried[1]) {
                            targetFlag = enemyFlagLocs[1];
                        } else if (enemyFlagLocs[2] != null && !targetsCarried[2]) {
                            targetFlag = enemyFlagLocs[2];
                        }
                    } else if (attackSquadTwo) {
                        if (enemyFlagLocs[1] != null && !targetsCarried[1]) {
                            targetFlag = enemyFlagLocs[1];
                        } else if (enemyFlagLocs[0] != null && !targetsCarried[0]) {
                            targetFlag = enemyFlagLocs[0];
                        } else if (enemyFlagLocs[2] != null && !targetsCarried[2]) {
                            targetFlag = enemyFlagLocs[2];
                        }
                    } else if (attackSquadThree) {
                        if (enemyFlagLocs[2] != null && !targetsCarried[2]) {
                            targetFlag = enemyFlagLocs[2];
                        } else if (enemyFlagLocs[1] != null && !targetsCarried[1]) {
                            targetFlag = enemyFlagLocs[1];
                        } else if (enemyFlagLocs[0] != null && !targetsCarried[0]) {
                            targetFlag = enemyFlagLocs[0];
                        }
                    }

                    if (targetFlag == null) {
                        if (broadCastLocs.length == 1) {
                            targetFlag = broadCastLocs[0];
                        } else if (broadCastLocs.length == 2) {
                            if (Comms.shortId > 25) {
                                targetFlag = broadCastLocs[0];
                            } else {
                                targetFlag = broadCastLocs[1];
                            }
                        } else if (broadCastLocs.length == 3) {
                            if (attackSquadOne) {
                                targetFlag = broadCastLocs[0];
                            } else if (attackSquadTwo) {
                                targetFlag = broadCastLocs[1];
                            } else {
                                targetFlag = broadCastLocs[2];
                            }
                        }
                    }

                    Direction dirToClosestBroadcastFromHomeFlag = null;
                    if (BUILDERSPECIALIST && homeFlag != null) {
                        int tempDist = Integer.MAX_VALUE;
                        MapLocation optBroadcast = null;
                        for (int i = broadCastLocs.length - 1; i >= 0; i--) {
                            int distToBC = homeFlag.distanceSquaredTo(broadCastLocs[i]);
                            if (distToBC < tempDist) {
                                tempDist = distToBC;
                                optBroadcast = broadCastLocs[i];
                            }
                        }
                        if (optBroadcast != null) {
                            dirToClosestBroadcastFromHomeFlag = homeFlag.directionTo(optBroadcast);
                        }
                    }

                    MapLocation escortTgt = null;
                    // set target flag as to null if someone is already escorting it.
                    // Only calculate after turn 200 cuz flag enemy flag can't be accessed until
                    // then
                    if (turnCount >= GameConstants.SETUP_ROUNDS) {
                        for (int i = enemyFlagLocs.length - 1; i >= 0; i--) {
                            if (enemyFlagLocs[i] != null && targetsCarried[i] && tgtLocation != null
                                    && tgtLocation.equals(enemyFlagLocs[i])) {
                                targetFlag = null;
                                tgtLocation = null;
                                turnsNotReachedTgt = 0;
                                lastTurnPursingCrumb = false;
                                if (rc.canSenseLocation(enemyFlagLocs[i])) {
                                    escortTgt = enemyFlagLocs[i];
                                }
                            }
                        }
                    }

                    // Enemy Counting, finding number of hostiles, number of hostiles in range, and
                    // the nearby hostile with the lowest HP
                    RobotInfo[] enemies = rc.senseNearbyRobots(rc.getLocation(), GameConstants.VISION_RADIUS_SQUARED,
                            rc.getTeam().opponent());
                    int numHostiles = 0;
                    int numHostilesIR = 0;
                    MapLocation lowestCurrHostile = null;
                    int lowestCurrHostileHealth = Integer.MAX_VALUE;
                    MapLocation closestHostile = null;
                    int distToClosestHostile = Integer.MAX_VALUE;

                    if (enemies.length != 0) {
                        for (int i = enemies.length - 1; i >= 0; i--) {
                            numHostiles++;
                            int tempDistToHostile = rc.getLocation().distanceSquaredTo(enemies[i].getLocation());
                            if (tempDistToHostile <= GameConstants.ATTACK_RADIUS_SQUARED) {
                                numHostilesIR++;
                            }
                            if (tempDistToHostile <= GameConstants.ATTACK_RADIUS_SQUARED
                                    && enemies[i].getHealth() < lowestCurrHostileHealth) {
                                lowestCurrHostileHealth = enemies[i].getHealth();
                                lowestCurrHostile = enemies[i].getLocation();
                            }
                            if (tempDistToHostile < distToClosestHostile) {
                                distToClosestHostile = tempDistToHostile;
                                closestHostile = enemies[i].getLocation();
                            }
                        }
                    }

                    // Friendly Counting, finding number of friendlies, number of friends in range,
                    // and nearby friend with lowest HP
                    RobotInfo[] friendlies = rc.senseNearbyRobots(rc.getLocation(), GameConstants.VISION_RADIUS_SQUARED,
                            rc.getTeam());
                    int numFriendlies = 0;
                    int numFriendliesIR = 0;
                    MapLocation lowestCurrFriendly = null;
                    int lowestCurrFriendlyHealth = Integer.MAX_VALUE;
                    MapLocation lowestCurrFriendlySeen = null;
                    int lowestCurrFriendlySeenHealth = Integer.MAX_VALUE;
                    if (friendlies.length != 0) {
                        for (int i = friendlies.length - 1; i >= 0; i--) {
                            numFriendlies++;
                            if (friendlies[i].getLocation()
                                    .distanceSquaredTo(rc.getLocation()) <= GameConstants.HEAL_RADIUS_SQUARED) {
                                numFriendliesIR++;
                            }
                            if (rc.getLocation()
                                    .distanceSquaredTo(friendlies[i].getLocation()) <= GameConstants.HEAL_RADIUS_SQUARED
                                    && friendlies[i].getHealth() < lowestCurrFriendlyHealth) {
                                lowestCurrFriendlyHealth = friendlies[i].getHealth();
                                lowestCurrFriendly = friendlies[i].getLocation();
                            }
                            if (friendlies[i].getHealth() < lowestCurrFriendlySeenHealth) {
                                lowestCurrFriendlySeenHealth = friendlies[i].getHealth();
                                lowestCurrFriendlySeen = friendlies[i].getLocation();
                            }

                        }
                    }

                    // nearby crumbs - find nearest high value crumb not on water
                    // TODO account for case where crumbs are surrounded completely by water
                    MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(GameConstants.VISION_RADIUS_SQUARED);
                    int largestCrumb = Integer.MIN_VALUE;
                    int distToLargestCrumb = Integer.MAX_VALUE;
                    MapLocation bigCloseCrumb = null;
                    for (int i = nearbyCrumbs.length - 1; i >= 0; i--) {
                        int crumbVal = rc.senseMapInfo(nearbyCrumbs[i]).getCrumbs();
                        int distCrumb = Math.max(Math.abs(rc.getLocation().x - nearbyCrumbs[i].x),
                                Math.abs(rc.getLocation().y - nearbyCrumbs[i].y));
                        if (crumbVal > largestCrumb) {
                            largestCrumb = crumbVal;
                            distToLargestCrumb = distCrumb;
                            bigCloseCrumb = nearbyCrumbs[i];
                        } else if (crumbVal == largestCrumb) {
                            if (distCrumb < distToLargestCrumb) {
                                largestCrumb = crumbVal;
                                distToLargestCrumb = distCrumb;
                                bigCloseCrumb = nearbyCrumbs[i];
                            }
                        }
                    }

                    // condition for when attackers can actually heal
                    // condition is if you're an attack specialist, the number of friendlies -
                    // number of hostiles has to be more than 5.
                    boolean attackerCanHeal = (!ATTACKSPECIALIST
                            || (ATTACKSPECIALIST && (numFriendlies - numHostiles > 6 && distToClosestHostile > 16)));

                    // boolean to declare if builder should stop training if you're a builder and
                    // your experience is >= 30 or its about time to fight (setup rounds-10)
                    boolean shouldNotTrain = (!BUILDERSPECIALIST ||
                            (BUILDERSPECIALIST && (rc.getExperience(SkillType.BUILD) >= 30
                                    || turnCount > GameConstants.SETUP_ROUNDS - 10)));

                    // when building explosive or stun traps, this is the preferred distance when
                    // building them away from one another
                    int explosiveTrapPreferredDist = Integer.MAX_VALUE;
                    int stunTrapPreferredDist = 2;
                    if (rc.getCrumbs() > 3000) {
                        explosiveTrapPreferredDist = 16;
                        stunTrapPreferredDist = 1;
                    } else if (rc.getCrumbs() > 200) {
                        stunTrapPreferredDist = 1;
                    }
                    // when building traps near enemies, this is how close the given trap should be
                    // relative to the enemy.
                    int buildThreshold = 12;
                    // disincentivize combat before this turn to allow for more useful activities
                    // during setup
                    int turnsTillAllowingCombat = 150;
                    // distance squared the sentry can be from the home flag
                    int sentryWanderingLimit = 12;
                    // distance squared to defend a flag
                    int distanceForDefense = 200;

                    // boolean representing if you should go home and trap
                    // if you're a builderspecialist and its between the given turns, and you have a
                    // missing initial trap and you have crumbs, go home and trap
                    // if its not between the turns, set to false.
                    if (BUILDERSPECIALIST
                            && ((turnCount > 100 && turnCount < turnsTillAllowingCombat)
                                    || rc.getExperience(SkillType.BUILD) >= 30)
                            && dirToClosestBroadcastFromHomeFlag != null) {
                        if ((!initialSetTrapStun || !initialSetTrapWater) && rc.getCrumbs() >= 100) {
                            shouldGoHomeAndTrap = true;
                        } else if (!(!initialSetTrapStun || !initialSetTrapWater)) {
                            shouldGoHomeAndTrap = false;
                        }
                    } else {
                        shouldGoHomeAndTrap = false;
                    }

                    // Role Delegation (outdated)
                    // If you have a flag, return
                    // else if your health is below retreat threshold with nearby enemies, you're
                    // wounded
                    // else if there are nearby enemies, you're in combat
                    // else if there is a nearby flag to be picked up, you're capturing
                    // else if you have experience over 30 and have more than 250 crumbs and have
                    // seen combat, you're building
                    // else if there is a close diplaced flag, you're defending
                    // else if you're lowest current friendly seen has a health below the dfault,
                    // you're healing
                    // else, you're scouting
                    if (rc.hasFlag()) {
                        role = RETURNING;
                        rc.setIndicatorString("Returning");
                    } else if (false && escortTgt != null) {
                        role = ESCORT;
                        rc.setIndicatorString("Escort " + targetFlag.toString());
                    } else if ((BUILDERSPECIALIST || rc.getLevel(SkillType.BUILD) > 3) && rc.getCrumbs() > 200
                            && (enemies.length != 0 && turnCount > GameConstants.SETUP_ROUNDS - 20)) {
                        role = BUILDING;
                        rc.setIndicatorString("Building");
                    } else if (shouldRespawn) {
                        // if its time to change shift, and you can't see your home flag location, go
                        // suicide to respawn
                        role = RESPAWN;
                        rc.setIndicatorString("Respawning");
                    } else if (shouldNotTrain && (lowestDistToDam == 1 || nearestDividerWithOpenNeighbor != null)
                            && turnCount > GameConstants.SETUP_ROUNDS - 40 && turnCount < GameConstants.SETUP_ROUNDS) {
                        role = LINEUP;
                        rc.setIndicatorString("LINEUP");
                    } else if (shouldNotTrain
                            && (bigCloseCrumb != null && turnCount > GameConstants.SETUP_ROUNDS - 40)) {
                        role = CRUMBS;
                        rc.setIndicatorString("CRUMBS: " + bigCloseCrumb.toString());
                    } else if (shouldNotTrain && Info.numFlagsNearbyNotPickedUp != 0) {
                        role = CAPTURING; // changed here
                        rc.setIndicatorString("Capturing");
                    } else if (shouldNotTrain && (enemies.length != 0 && turnCount > GameConstants.SETUP_ROUNDS)) {
                        role = INCOMBAT;
                        haveSeenCombat = true;
                        rc.setIndicatorString("In combat");
                    } else if (BUILDERSPECIALIST && !shouldGoHomeAndTrap
                            && diggable != null && rc.getExperience(SkillType.BUILD) < 30) {
                        role = TRAINBUILD;
                        rc.setIndicatorString("Training builder: " + diggable.toString());
                    } else if (closestDisplacedFlag != null &&
                            rc.senseMapInfo(rc.getLocation()).getTeamTerritory().equals(rc.getTeam())
                            && rc.getLocation().distanceSquaredTo(closestDisplacedFlag) < distanceForDefense) {
                        role = DEFENDING;
                        rc.setIndicatorString("Defending");
                    } else if (lowestCurrFriendlySeenHealth < GameConstants.DEFAULT_HEALTH) {
                        role = HEALING;
                        rc.setIndicatorString("Healing");
                    } else if (SENTRY && !retireSentry) {
                        role = SENTRYING;
                        rc.setIndicatorString("Sentry");
                    } else {
                        role = SCOUTING;
                        rc.setIndicatorString("Scouting");
                    }

                    if (role == SCOUTING) {
                        Direction optimalDir = null;

                        // if you're a builder and you have the experience and crumbs, and its before a
                        // certain turn, go home to set a stun trap
                        // if you are near home, sense if you can set a stun trap

                        // builders visit home during setup before turn 150 to see if they can trap
                        // their homes
                        // if you have lvl 6 building, have over 100 crumbs, no enemies are around, and
                        // can sense your home location, check if you can stun trap corners.
                        if ((BUILDERSPECIALIST || rc.getLevel(SkillType.BUILD) > 3)
                                && dirToClosestBroadcastFromHomeFlag != null) {
                            if (shouldGoHomeAndTrap && !rc.canSenseLocation(homeFlag)) {
                                optimalDir = Pathfinder.pathfind(rc.getLocation(), homeFlag);
                                rc.setIndicatorString("Scouting: Going home to see if I can set traps");
                            } else if (rc.canSenseLocation(homeFlag) && rc.getCrumbs() >= 100) {
                                // TODO this is another opportunity to see if home flag still exists to update
                                // in comms
                                boolean homeFlagStillExists = false;
                                FlagInfo[] nearbyAllyFlags = rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED,
                                        rc.getTeam());
                                for (int i = nearbyAllyFlags.length - 1; i >= 0; i--) {
                                    if (nearbyAllyFlags[i].getLocation().equals(homeFlag)) {
                                        homeFlagStillExists = true;
                                        break;
                                    }
                                }
                                if (homeFlagStillExists) {
                                    MapLocation closestViableLocWithoutTrap = null;
                                    int distToClosestViableLoc = Integer.MAX_VALUE;
                                    for (int i = 1; i >= 0; i--) {
                                        MapLocation spawnCheck = null;
                                        if (i == 1) {
                                            spawnCheck = new MapLocation(homeFlag.x, homeFlag.y);
                                            if (rc.canSenseLocation(spawnCheck)
                                                    && rc.senseMapInfo(spawnCheck).getTrapType() == TrapType.STUN) {
                                                initialSetTrapStun = true;
                                            }
                                        } else if (i == 0) {
                                            spawnCheck = homeFlag.add(dirToClosestBroadcastFromHomeFlag);
                                            if (rc.canSenseLocation(spawnCheck)
                                                    && rc.senseMapInfo(spawnCheck).getTrapType() == TrapType.WATER) {
                                                initialSetTrapWater = true;
                                            }
                                        }
                                        int distSqToCorner = rc.getLocation().distanceSquaredTo(spawnCheck);
                                        if (rc.canSenseLocation(spawnCheck)
                                                && rc.senseMapInfo(spawnCheck).getTrapType() == TrapType.NONE
                                                && distSqToCorner < distToClosestViableLoc) {
                                            distToClosestViableLoc = distSqToCorner;
                                            closestViableLocWithoutTrap = spawnCheck;
                                        }
                                    }

                                    if (closestViableLocWithoutTrap != null) {
                                        rc.setIndicatorString(
                                                "Scouting: Trapping: " + closestViableLocWithoutTrap.toString());
                                        if (closestViableLocWithoutTrap.equals(homeFlag)
                                                && rc.canBuild(TrapType.STUN, closestViableLocWithoutTrap)) {
                                            rc.build(TrapType.STUN, closestViableLocWithoutTrap);
                                        } else if (closestViableLocWithoutTrap
                                                .equals(homeFlag.add(dirToClosestBroadcastFromHomeFlag))
                                                && rc.canBuild(TrapType.WATER, closestViableLocWithoutTrap)) {
                                            rc.build(TrapType.WATER, closestViableLocWithoutTrap);
                                        } else {
                                            optimalDir = Pathfinder.pathfind(rc.getLocation(),
                                                    closestViableLocWithoutTrap);
                                        }
                                    }
                                }
                            }
                        }

                        // Otherwise, if optimalDir is null after these builder specific conditions,
                        // pursue nearby crumbs
                        // If there are no nearby crumbs, go to either a random location, or if it is
                        // beyond a certain turn, go to the targetFlag.
                        if (optimalDir == null) {
                            if (bigCloseCrumb != null) {
                                tgtLocation = bigCloseCrumb;
                                turnsNotReachedTgt = 0;
                                lastTurnPursingCrumb = true;
                            } else if (turnCount > turnsTillAllowingCombat && targetFlag != null
                                    && !rc.canSenseLocation(targetFlag)) {
                                tgtLocation = targetFlag;
                                turnsNotReachedTgt = 0;
                            } else if (tgtLocation == null || rc.getLocation().equals(tgtLocation) ||
                                    (rc.canSenseLocation(tgtLocation) && !rc.sensePassability(tgtLocation))
                                    || turnsNotReachedTgt > 50 || lastTurnPursingCrumb) {
                                tgtLocation = generateRandomMapLocation(3, rc.getMapWidth() - 3,
                                        3, rc.getMapHeight() - 3);
                                turnsNotReachedTgt = 0;
                                lastTurnPursingCrumb = false;
                            }
                            optimalDir = Pathfinder.pathfind(rc.getLocation(), tgtLocation);
                            if (tgtLocation != null) {
                                rc.setIndicatorString("Scouting: Tgt: " + tgtLocation.toString());
                            }
                        }

                        // if you're a builderspecialist and you need to train, check to see if you can
                        // train before and after moving.
                        // dig any nearby water, healmove, then dig any nearby water
                        // TODO: potentially should dig nearby water, then call pathfinding, then dig
                        // again to see if digging that space gives pathfinder an option to move
                        if (BUILDERSPECIALIST && rc.getExperience(SkillType.BUILD) < 30) {
                            trainToSixByDigging(rc);
                        }
                        if (lastTurnPursingCrumb || turnCount > 150) {
                            clearTheWay(rc);
                        }
                        healMove(rc, optimalDir, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);
                        if (BUILDERSPECIALIST && rc.getExperience(SkillType.BUILD) < 30) {
                            trainToSixByDigging(rc);
                        }
                        if (lastTurnPursingCrumb || turnCount > 150) {
                            clearTheWay(rc);
                        }
                    } else if (role == INCOMBAT) {

                        Direction optimalDir = null;

                        // if (turnsPursuingStun > 0) {
                        // optimalDir = findOptimalPursuingStunDir(rc, enemies,
                        // averageDistSqFromEnemies);
                        // rc.setIndicatorString("In Combat, pursuing stun!");
                        // turnsPursuingStun--;
                        // } else if (lastTurnClosestStunTrap != null &&
                        // rc.canSenseLocation(lastTurnClosestStunTrap) &&
                        // rc.senseMapInfo(lastTurnClosestStunTrap).getTrapType().equals(TrapType.NONE))
                        // {
                        // optimalDir = findOptimalPursuingStunDir(rc, enemies,
                        // averageDistSqFromEnemies);
                        // turnsPursuingStun = 4;
                        // rc.setIndicatorString("In Combat, Last turn closest Stun trap exploded,
                        // Attacking");
                        // } else if (nearestStunTrap != null && closestHostile != null) {
                        //
                        // optimalDir = findOptimalTrapKiteDir(rc, closestHostile, enemies,
                        // nearestStunTrap);
                        //
                        // if (optimalDir != null) {
                        // rc.setIndicatorString("In Combat, Trap Kiting: " + optimalDir.toString());
                        // //System.out.println("In Combat, Trap Kiting: " + optimalDir.toString());
                        // } else {
                        // //System.out.println("In Combat, Trap Kiting: Null");
                        // rc.setIndicatorString("In Combat, Trap Kiting: Null");
                        // }
                        //
                        // } else {
                        //
                        // optimalDir = findOptimalCombatDir(rc, enemies, averageDistSqFromEnemies,
                        // woundedRetreatThreshold, numHostiles, numFriendlies);
                        // if (optimalDir != null) {
                        // rc.setIndicatorString("In Combat, Not Trap Kiting: " +
                        // optimalDir.toString());
                        // } else {
                        // rc.setIndicatorString("In Combat, Not Trap Kiting: Null");
                        // }
                        //
                        // }

                        // if you're a sentry, you cannot leave sight of the flag, so you are confined
                        // to a wandering limit
                        // Calculate the average distance from all enemies.
                        float averageDistSqFromEnemies = averageDistanceSquaredFrom(enemies, rc.getLocation());
                        optimalDir = findOptimalCombatDir(rc, enemies, lowestCurrHostile, closestHostile,
                                averageDistSqFromEnemies,
                                numHostiles, numFriendlies);
                        boolean shouldProtectAtAllCosts = closestDisplacedFlag != null
                                && rc.getLocation().distanceSquaredTo(closestDisplacedFlag) < 20;
                        if (shouldProtectAtAllCosts) {
                            optimalDir = Pathfinder.pathfind(rc.getLocation(), closestDisplacedFlag);
                        }
                        if (optimalDir != null) {
                            if (!SENTRY || retireSentry || (SENTRY && !retireSentry && rc.getLocation().add(optimalDir)
                                    .distanceSquaredTo(homeFlag) < sentryWanderingLimit)) {
                                attackMove(rc, optimalDir, lowestCurrHostile, lowestCurrHostileHealth);
                            }
                        } else {
                            rc.setIndicatorString("combat null: " + averageDistSqFromEnemies);
                            attackMove(rc, optimalDir, lowestCurrHostile, lowestCurrHostileHealth);
                        }
                    } else if (role == BUILDING) {

                        Direction optimalDir = null;
                        // dummy mapLocation. Build whlie cooldown + buildCD< 10 and build != null.
                        // laytrapwithinrangeofenemy will return either null (if nothing is built) or
                        // the maplocation of the trap that is built
                        // update nearest stun and explosive trap with this information and repeat while
                        // loop until cd restriction or return null
                        // signifying htat there is nowhere to build.
                        // TODO: check if can build before and after move
                        MapLocation build = new MapLocation(0, 0);
                        int buildCD = SkillType.BUILD.getCooldown(rc.getLevel(SkillType.BUILD));
                        while (rc.getActionCooldownTurns() + buildCD < 10 && build != null) {
                            build = layTrapWithinRangeOfEnemy(rc, nearestExplosiveTrap, nearestStunTrap, enemies,
                                    closestHostile,
                                    explosiveTrapPreferredDist, stunTrapPreferredDist, buildThreshold);
                            if (build != null) {
                                MapInfo updateLoc = rc.senseMapInfo(build);
                                int distToUpdate = rc.getLocation().distanceSquaredTo(build);
                                if (updateLoc.getTrapType() == TrapType.STUN && distToUpdate < lowestDistToStunTrap) {
                                    nearestStunTrap = build;
                                    lowestDistToStunTrap = distToUpdate;
                                } else if (updateLoc.getTrapType() == TrapType.EXPLOSIVE
                                        && distToUpdate < lowestDistToExplosiveTrap) {
                                    nearestExplosiveTrap = build;
                                    lowestDistToExplosiveTrap = distToUpdate;
                                }
                            }
                        }
                        // Calculate the average distance from all enemies.
                        float averageDistSqFromEnemies = averageDistanceSquaredFrom(enemies, rc.getLocation());
                        optimalDir = findOptimalCombatDir(rc, enemies, lowestCurrHostile, closestHostile,
                                averageDistSqFromEnemies,
                                numHostiles, numFriendlies);
                        attackMove(rc, optimalDir, lowestCurrHostile, lowestCurrHostileHealth);
                        if (optimalDir == null) {
                            rc.setIndicatorString("building null: " + averageDistSqFromEnemies);
                        }

                    } else if (role == CAPTURING) {
                        // If you can pick up the flag, pick it up, otherwise calculate the nearest
                        // enemy flag, and go to it

                        Direction dir;
                        if (rc.canPickupFlag(Info.closestFlag)) {
                            rc.pickupFlag(Info.closestFlag);
                            dir = Pathfinder.pathfindHome();
                        } else {
                            dir = Pathfinder.pathfind(rc.getLocation(), Info.closestFlag);
                            if (rc.canPickupFlag(Info.closestFlag)) {
                                rc.pickupFlag(Info.closestFlag);
                            }
                        }

                        healMove(rc, dir, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);

                    } else if (role == RETURNING) {
                        // If we are holding an enemy flag, singularly focus on moving towards
                        // an ally spawn zone to capture it! We use the check roundNum >= SETUP_ROUNDS
                        // to make sure setup phase has ended.
                        if (rc.hasFlag() && rc.getRoundNum() >= GameConstants.SETUP_ROUNDS) {
                            // if there is a friendly with
                            // this.travelDistanceHome - friendly.travelDistanceHome == 1-2 in direction of
                            // home
                            // drop flag in their direction

                            // MapLocation relay = findFlagRelay();
                            // if (!relay.equals(new MapLocation(-1, -1))) {
                            // Direction dir = Pathfinder
                            // .passableDirectionTowards(rc.getLocation().directionTo(relay));
                            // if (dir != Direction.CENTER && rc.canDropFlag(rc.adjacentLocation(dir))) {
                            // rc.dropFlag(rc.adjacentLocation(dir));
                            // }
                            // }
                            // If you dropped the flag you wouldn't be able to move anyways
                            // backtrack home along waypoint list
                            MapLocation currTgt = prevWaypoints[prevWaypointIndex];
                            if (currTgt != null) {
                                Direction dir = Pathfinder.pathfind(rc.getLocation(), currTgt);
                                if (rc.canMove(dir)) {
                                    rc.move(dir);
                                }
                                // if you can see this waypoint, pop it and ready to move to prev one
                                if (rc.canSenseLocation(currTgt)) {
                                    prevWaypoints[prevWaypointIndex] = null;
                                    prevWaypointIndex = (prevWaypointIndex - 1) < 0 ? prevWaypoints.length - 1
                                            : prevWaypointIndex - 1;
                                }
                                // if you can see home just go
                                if (rc.canSenseLocation(Info.closestSpawn)) {
                                    prevWaypoints = new MapLocation[20];
                                }
                                // once we exausted all waypoints just go back home
                            } else {
                                Direction dir = Pathfinder.pathfindHome();
                                if (rc.canMove(dir)) {
                                    rc.move(dir);
                                }
                            }

                        }
                    } else if (role == DEFENDING) {

                        Direction dir = Pathfinder.pathfind(rc.getLocation(), closestDisplacedFlag);
                        healMove(rc, dir, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);

                        rc.setIndicatorString("Defending" + closestDisplacedFlag.toString());
                    } else if (role == HEALING) {

                        // if you're a sentry, you cannot leave sight of the flag, so you are confined
                        // to a wandering limit
                        Direction dir = Pathfinder.pathfind(rc.getLocation(), lowestCurrFriendlySeen);
                        if (!SENTRY || retireSentry || (SENTRY && !retireSentry
                                && rc.getLocation().add(dir).distanceSquaredTo(homeFlag) < sentryWanderingLimit)) {
                            healMove(rc, dir, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);
                        }

                    } else if (role == SENTRYING) {

                        // always path to the homeFlag when sentrying
                        Direction dir = Pathfinder.pathfind(rc.getLocation(), homeFlag);
                        if (rc.canMove(dir)) {
                            rc.move(dir);
                        }
                        rc.setIndicatorString("Sentrying: Target: " + homeFlag.toString());

                    } else if (role == RESPAWN) {
                        // respawn by going to the nearest hostile or if that is null, the nearest
                        // broadcast flag.
                        if (closestHostile != null) {
                            Direction dir = Pathfinder.pathfind(rc.getLocation(), closestHostile);
                            if (enemies.length != 0) {
                                attackMove(rc, dir, lowestCurrHostile, lowestCurrHostileHealth);
                            } else {
                                healMove(rc, dir, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);
                            }
                            rc.setIndicatorString("Respawning target: Hostile " + closestHostile.toString());
                        } else if (targetFlag != null) {
                            Direction dir = Pathfinder.pathfind(rc.getLocation(), targetFlag);
                            if (enemies.length != 0) {
                                attackMove(rc, dir, lowestCurrHostile, lowestCurrHostileHealth);
                            } else {
                                healMove(rc, dir, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);
                            }
                            rc.setIndicatorString("Respawning target: Broadcast " + targetFlag.toString());
                        }
                    } else if (role == CRUMBS) {

                        Direction pathToCrumb = Pathfinder.pathfind(rc.getLocation(), bigCloseCrumb);
                        if (enemies.length != 0) {
                            attackMove(rc, pathToCrumb, lowestCurrHostile, lowestCurrHostileHealth);
                        } else if (nearestWater != null) {
                            if (lowestCurrFriendly != null) {
                                healMove(rc, pathToCrumb, lowestCurrFriendly, lowestCurrFriendlyHealth,
                                        attackerCanHeal);
                                clearTheWay(rc);
                            } else {
                                clearTheWay(rc);
                                healMove(rc, pathToCrumb, lowestCurrFriendly, lowestCurrFriendlyHealth,
                                        attackerCanHeal);
                                clearTheWay(rc);
                            }
                        } else {
                            healMove(rc, pathToCrumb, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);
                        }

                    } else if (role == LINEUP) {
                        if (nearestDividerWithOpenNeighbor != null && lowestDistToDam != 1) {
                            Direction pathToDivider = Pathfinder.pathfind(rc.getLocation(),
                                    nearestDividerWithOpenNeighbor);
                            clearTheWay(rc);
                            healMove(rc, pathToDivider, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);
                            clearTheWay(rc);
                            rc.setIndicatorString("Lining up: " + nearestDividerWithOpenNeighbor.toString());
                        } else {
                            rc.setIndicatorString("Lined up!");
                        }
                    } else if (role == ESCORT) {
                        Direction optimalDir = null;
                        if (rc.getLocation().distanceSquaredTo(escortTgt) > 10) {
                            optimalDir = Pathfinder.pathfind(rc.getLocation(), escortTgt);
                        } else {
                            optimalDir = Pathfinder.pathfind(rc.getLocation(), escortTgt).opposite();
                        }
                        if (closestHostile != null) {
                            attackMove(rc, optimalDir, lowestCurrHostile, lowestCurrHostileHealth);
                        } else {
                            healMove(rc, optimalDir, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);
                        }
                    } else if (role == TRAINBUILD) {
                        trainToSixByDigging(rc);
                        Direction optimalDir = null;
                        if (diggable != null) {
                            optimalDir = Pathfinder.pathfind(rc.getLocation(), diggable);
                            if (closestHostile != null) {
                                attackMove(rc, optimalDir, lowestCurrHostile, lowestCurrHostileHealth);
                            } else {
                                healMove(rc, optimalDir, lowestCurrFriendly, lowestCurrFriendlyHealth, attackerCanHeal);
                            }
                        }
                        trainToSixByDigging(rc);
                    }

                    while (lowestCurrFriendly != null && rc.canHeal(lowestCurrFriendly) &&
                            (closestHostile == null || rc.getLocation().distanceSquaredTo(closestHostile) > 10)) {
                        rc.heal(lowestCurrFriendly);
                    }

                    if (turnCount > 1900 && rc.getExperience(SkillType.BUILD) < 15) {
                        trainToSixByDigging(rc);
                    }

                    // Sentry comm updates to information about home flags.
                    if (SENTRY) {
                        // TODO this is an opportunity to update if homeFlag exists in comms
                        // if you're location is the same as the closest default, check if its still
                        // there. If not, retire sentry.
                        MapLocation[] allyFlags = null;
                        if (turnCount <= 200) {
                            allyFlags = Comms.getDefaultAllyFlagLocations();
                        } else {
                            allyFlags = Comms.getCurrentAllyFlagLocations();
                        }
                        retireSentry = !(allyFlags[homeFlagIndex] != null && allyFlags[homeFlagIndex].equals(homeFlag));
                    }

                    // increment turnsnotreachedtarget if you are not at your target location.
                    if (tgtLocation != null && !rc.getLocation().equals(tgtLocation)) {
                        turnsNotReachedTgt++;
                    }

                    // rc.setIndicatorString("ShortId: " + Comms.shortId);

                    // // default battlecode code:
                    // else {
                    // if (rc.canPickupFlag(rc.getLocation())) {
                    // rc.pickupFlag(rc.getLocation());
                    // }
                    // // If we are holding an enemy flag, singularly focus on moving towards
                    // // an ally spawn zone to capture it! We use the check roundNum >=
                    // SETUP_ROUNDS
                    // // to make sure setup phase has ended.
                    // if (rc.hasFlag() && rc.getRoundNum() >= GameConstants.SETUP_ROUNDS) {
                    // MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                    // MapLocation firstLoc = spawnLocs[0];
                    // Direction dir = rc.getLocation().directionTo(firstLoc);
                    // if (rc.canMove(dir))
                    // rc.move(dir);
                    // }
                    // // Move and attack randomly if no objective.
                    // Direction dir = Pathfinder.pathfind(rc.getLocation(), new MapLocation(29,
                    // 0));
                    // MapLocation nextLoc = rc.getLocation().add(dir);
                    // if (rc.canMove(dir)) {
                    // rc.move(dir);
                    // } else if (rc.canAttack(nextLoc)) {
                    // rc.attack(nextLoc);
                    // System.out.println("Take that! Damaged an enemy that was in our way!");
                    // }
                    //
                    // // Rarely attempt placing traps behind the robot.
                    // MapLocation prevLoc = rc.getLocation().subtract(dir);
                    // if (rc.canBuild(TrapType.EXPLOSIVE, prevLoc) && rng.nextInt() % 37 == 1)
                    // rc.build(TrapType.EXPLOSIVE, prevLoc);
                    // // We can also move our code into different methods or classes to better
                    // // organize it!
                    // updateEnemyRobots(rc);

                    // record all enemies within 10 r^2(capable of attacking you in their next turn)
                    prevEnemiesIn1Step.clear();
                    RobotInfo[] currEnemiesIn1Step = rc.senseNearbyRobots(rc.getLocation(), 10,
                            rc.getTeam().opponent());
                    for (RobotInfo ri : currEnemiesIn1Step) {
                        prevEnemiesIn1Step.add(ri.getID());
                    }
                    prevHp = rc.getHealth();

                    // if alive update waypoint list as needed
                    if (role != RETURNING && turnsAlive % 5 == 0 && rc.getRoundNum() > 200) {
                        MapLocation prevWP = prevWaypoints[prevWaypointIndex];
                        if (prevWP == null
                                || Pathfinder.trueTravelDistance(prevWP, rc.getLocation()) >= WAYPOINT_SPACING) {
                            prevWaypointIndex = (prevWaypointIndex + 1) % prevWaypoints.length;
                            prevWaypoints[prevWaypointIndex] = rc.getLocation();
                        }
                    }
                }
                Comms.update();

            }
            // catch (GameActionException e) {
            // // Oh no! It looks like we did something illegal in the Battlecode world. You
            // // should
            // // handle GameActionExceptions judiciously, in case unexpected events occur
            // in
            // // the game
            // // world. Remember, uncaught exceptions cause your robot to explode!
            // System.out.println("GameActionException");
            // e.printStackTrace();
            //
            // } catch (Exception e) {
            // // Oh no! It looks like our code tried to do something bad. This isn't a
            // // GameActionException, so it's more likely to be a bug in our code.
            // System.out.println("Exception");
            // e.printStackTrace();
            //
            // }
            finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop
                // again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for
            // another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction
        // imminent...
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException {
        // Sensing methods can be passed in a radius of -1 to automatically
        // use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0) {
            // Save an array of locations with enemy robots in them for future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++) {
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            // Let the rest of our team know how many enemy robots we see!
            if (rc.canWriteSharedArray(0, enemyRobots.length)) {
                rc.writeSharedArray(0, enemyRobots.length);
                int numEnemies = rc.readSharedArray(0);
            }
        }
    }

    /**
     * GenerateRandomMapLocation generates a random map location within the max and
     * min bounds given.
     *
     * @param xMin minimum x value of MapLocation
     * @param xMax maximum x value of MapLocation
     * @param yMin minimum y value of MapLocation
     * @param yMax maximum y value of MapLocation
     * @return random map location within the max and min bounds given
     */
    public static MapLocation generateRandomMapLocation(int xMin, int xMax, int yMin, int yMax) {
        int randX = rng.nextInt(xMax - xMin) + xMin;
        int randY = rng.nextInt(yMax - yMin) + yMin;
        MapLocation randTgt = new MapLocation(randX, randY);
        return randTgt;
    }

    /**
     * Building a trap (taking into account spacing)
     *
     * @param rc                         robotcontroller
     * @param nearestExplosiveTrap       maplocation of nearest explosive trap
     * @param nearestStunTrap            maplocation of nearest stun trap
     * @param explosiveTrapPreferredDist distance an explosive trap should be away
     *                                   from another
     * @param stunTrapPreferredDist      distance a stun trap should be away from
     *                                   another
     * @throws GameActionException
     */
    public static void layTrap(RobotController rc, MapLocation nearestExplosiveTrap, MapLocation nearestStunTrap,
            int explosiveTrapPreferredDist, int stunTrapPreferredDist)
            throws GameActionException {
        // Iterate through all building directions, and go through the following logic:
        // 1 . If there are no nearby Explosive traps, build one,
        // 2. Else if there are no nearby Stun Traps, build one.
        // 3. Else if there are both, prioritize builiding another explosive trap that
        // is at least explosiveTrapPreferredDist sq units away from the first one
        // 4. IF you can't build the explosive trap, but can build the stun trap at
        // least stunTrapPreferredDist sq units away from the first one, do so.
        for (int i = Direction.allDirections().length - 1; i >= 0; i--) {
            MapLocation buildLoc = rc.getLocation().add(Direction.allDirections()[i]);
            if (nearestExplosiveTrap == null) {
                if (rc.canBuild(TrapType.EXPLOSIVE, buildLoc)) {
                    rc.build(TrapType.EXPLOSIVE, buildLoc);
                    break;
                }
            } else if (nearestStunTrap == null) {
                if (rc.canBuild(TrapType.STUN, buildLoc)) {
                    rc.build(TrapType.STUN, buildLoc);
                    break;
                }
            } else {
                int distTonearestExplosiveTrap = buildLoc.distanceSquaredTo(nearestExplosiveTrap);
                int distTonearestStunTrap = buildLoc.distanceSquaredTo(nearestStunTrap);

                if (distTonearestExplosiveTrap > explosiveTrapPreferredDist
                        && rc.canBuild(TrapType.EXPLOSIVE, buildLoc)) {
                    // checking for traps at the new build location to ensure that there is no traps
                    // there that would be closer to the closest trap given.
                    MapInfo[] tilesToCheckForCloserExplosiveTrap = rc.senseNearbyMapInfos(buildLoc,
                            explosiveTrapPreferredDist);

                    boolean passedCheckForCloserTrap = true;
                    for (int j = tilesToCheckForCloserExplosiveTrap.length - 1; j >= 0; j--) {
                        if (tilesToCheckForCloserExplosiveTrap[j].getTrapType() == TrapType.EXPLOSIVE) {
                            passedCheckForCloserTrap = false;
                            break;
                        }
                    }

                    if (passedCheckForCloserTrap) {
                        rc.build(TrapType.EXPLOSIVE, buildLoc);
                    }
                    break;
                } else if (distTonearestStunTrap > stunTrapPreferredDist
                        && rc.canBuild(TrapType.STUN, buildLoc)) {
                    // checking for traps at the new build location to ensure that there is no traps
                    // there that would be closer to the closest trap given.
                    MapInfo[] tilesToCheckForCloserStunTrap = rc.senseNearbyMapInfos(buildLoc, stunTrapPreferredDist);

                    boolean passedCheckForCloserTrap = true;
                    for (int j = tilesToCheckForCloserStunTrap.length - 1; j >= 0; j--) {
                        if (tilesToCheckForCloserStunTrap[j].getTrapType() == TrapType.STUN) {
                            passedCheckForCloserTrap = false;
                            break;
                        }
                    }

                    if (passedCheckForCloserTrap) {
                        rc.build(TrapType.STUN, buildLoc);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Building a trap (taking into account trap spacing) within the given range of
     * the closest enemy returns location of built trap.
     *
     * @param rc                         robotcontroller
     * @param nearestExplosiveTrap       maplocation of nearest explosive trap
     * @param nearestStunTrap            maplocation of nearest stun trap
     * @param closestEnemy               maplocation of nearest enemy trap
     * @param explosiveTrapPreferredDist distance an explosive trap should be away
     *                                   from another
     * @param stunTrapPreferredDist      distance a stun trap should be away from
     *                                   another
     * @param buildThreshold             max distance squared where a trap should be
     *                                   build
     *                                   from the enemy
     * @throws GameActionException
     */
    public static MapLocation layTrapWithinRangeOfEnemy(RobotController rc, MapLocation nearestExplosiveTrap,
            MapLocation nearestStunTrap, RobotInfo[] enemies, MapLocation closestEnemy, int explosiveTrapPreferredDist,
            int stunTrapPreferredDist,
            int buildThreshold) throws GameActionException {
        // Iterate through all building directions, and go through the following logic:
        // 1 . If there are no nearby Explosive traps, build one,
        // 2. Else if there are no nearby Stun Traps, build one.
        // 3. Else if there are both, prioritize builiding another explosive trap that
        // is at least explosiveTrapPreferredDist sq units away from the first one
        // 4. IF you can't build the explosive trap, but can build the stun trap at
        // least stunTrapPreferredDist sq units away from the first one, do so.
        // TODO build twice a turn?
        MapLocation built = null;
        float closestToEnemy = Integer.MAX_VALUE;
        MapLocation closestDirToEnemy = null;
        for (int i = Direction.allDirections().length - 1; i >= 0; i--) {
            MapLocation buildLoc = rc.getLocation().add(Direction.allDirections()[i]);
            float avgDistEnemy = averageDistanceSquaredFrom(enemies, buildLoc);
            if (rc.canBuild(TrapType.STUN, buildLoc) && buildLoc.distanceSquaredTo(closestEnemy) <= buildThreshold
                    && avgDistEnemy < closestToEnemy) {
                closestToEnemy = avgDistEnemy;
                closestDirToEnemy = buildLoc;
            }
        }
        if (closestDirToEnemy != null) {
            // if (nearestExplosiveTrap == null) {
            // if (rc.canBuild(TrapType.EXPLOSIVE, closestDirToEnemy)) {
            // rc.build(TrapType.EXPLOSIVE, closestDirToEnemy);
            // built = closestDirToEnemy;
            // }
            // } else if (nearestStunTrap == null) {
            // if (rc.canBuild(TrapType.STUN, closestDirToEnemy)) {
            // rc.build(TrapType.STUN, closestDirToEnemy);
            // built = closestDirToEnemy;
            // }
            // } else {
            int distTonearestExplosiveTrap = Integer.MAX_VALUE;
            int distTonearestStunTrap = Integer.MAX_VALUE;

            if (nearestExplosiveTrap != null) {
                distTonearestExplosiveTrap = closestDirToEnemy.distanceSquaredTo(nearestExplosiveTrap);
            }
            if (nearestStunTrap != null) {
                distTonearestStunTrap = closestDirToEnemy.distanceSquaredTo(nearestStunTrap);
            }

            if (distTonearestStunTrap > stunTrapPreferredDist
                    && rc.canBuild(TrapType.STUN, closestDirToEnemy)) {
                // checking for traps at the new build location to ensure that there is no traps
                // there that would be closer to the closest trap given.
                MapInfo[] tilesToCheckForCloserStunTrap = rc.senseNearbyMapInfos(closestDirToEnemy,
                        stunTrapPreferredDist);

                boolean passedCheckForCloserTrap = true;
                for (int j = tilesToCheckForCloserStunTrap.length - 1; j >= 0; j--) {
                    if (tilesToCheckForCloserStunTrap[j].getTrapType() == TrapType.STUN) {
                        passedCheckForCloserTrap = false;
                        break;
                    }
                }

                if (passedCheckForCloserTrap) {
                    rc.build(TrapType.STUN, closestDirToEnemy);
                    built = closestDirToEnemy;
                }
            } else if (distTonearestExplosiveTrap > explosiveTrapPreferredDist
                    && rc.canBuild(TrapType.EXPLOSIVE, closestDirToEnemy)) {
                // checking for traps at the new build location to ensure that there is no traps
                // there that would be closer to the closest trap given.
                MapInfo[] tilesToCheckForCloserExplosiveTrap = rc.senseNearbyMapInfos(closestDirToEnemy,
                        explosiveTrapPreferredDist);
                boolean passedCheckForCloserTrap = true;
                for (int j = tilesToCheckForCloserExplosiveTrap.length - 1; j >= 0; j--) {
                    if (tilesToCheckForCloserExplosiveTrap[j].getTrapType() == TrapType.EXPLOSIVE) {
                        passedCheckForCloserTrap = false;
                        break;
                    }
                }

                if (passedCheckForCloserTrap) {
                    rc.build(TrapType.EXPLOSIVE, closestDirToEnemy);
                    built = closestDirToEnemy;
                }
            }
            // }
        }
        return built;
    }

    /**
     * attackMove ensures that you are attacking the lowest health enemy by deciding
     * when to attack first or move first
     *
     * @param rc                      robot controller
     * @param optimalDir              optimal direction that was decided to move
     * @param lowestCurrHostile       current hostile with the lowest health
     * @param lowestCurrHostileHealth health of the current hostile with the lowest
     *                                health
     * @throws GameActionException
     */
    public static void attackMove(RobotController rc, Direction optimalDir, MapLocation lowestCurrHostile,
            int lowestCurrHostileHealth) throws GameActionException {
        // Calculate what would be the lowest health of a hostile after a movement.
        MapLocation aflowestCurrHostile = null;
        int aflowestCurrHostileHealth = Integer.MAX_VALUE;

        if (optimalDir != null) {
            RobotInfo[] afhostiles = rc.senseNearbyRobots(rc.getLocation().add(optimalDir),
                    GameConstants.ATTACK_RADIUS_SQUARED, rc.getTeam().opponent());

            for (int i = afhostiles.length - 1; i >= 0; i--) {

                if (afhostiles[i].getHealth() < aflowestCurrHostileHealth) {
                    aflowestCurrHostileHealth = afhostiles[i].getHealth();
                    aflowestCurrHostile = afhostiles[i].getLocation();
                }

            }
        }

        // 1. if there is a hostile within range and not one after you move,
        // while you can attack it, do so and move to the optimal dir
        // 2. else if there is a hostile after moving and not one currently, move, then
        // attack
        // 3. else if, if both exist, choose move or attack order based on which would
        // yield damage to the lowest health enemy.
        // 4. else if they both don't exist, move in optimal dir
        if (aflowestCurrHostile == null && lowestCurrHostile != null) {
            while (rc.canAttack(lowestCurrHostile)) {
                rc.attack(lowestCurrHostile);
            }
            if (optimalDir != null) {
                if (rc.canMove(optimalDir)) {
                    rc.move(optimalDir);
                }
            }
        } else if (aflowestCurrHostile != null && lowestCurrHostile == null) {
            if (rc.canMove(optimalDir)) {
                rc.move(optimalDir);
            }
            while (rc.canAttack(aflowestCurrHostile)) {
                rc.attack(aflowestCurrHostile);
            }
        } else if (aflowestCurrHostile != null && lowestCurrHostile != null) {
            if (aflowestCurrHostileHealth < lowestCurrHostileHealth) {
                if (rc.canMove(optimalDir)) {
                    rc.move(optimalDir);
                }
                while (rc.canAttack(aflowestCurrHostile)) {
                    rc.attack(aflowestCurrHostile);
                }
            } else {
                while (lowestCurrHostile != null && rc.canAttack(lowestCurrHostile)) {
                    rc.attack(lowestCurrHostile);
                }
                if (optimalDir != null) {
                    if (rc.canMove(optimalDir)) {
                        rc.move(optimalDir);
                    }
                }
            }
        } else {
            if (optimalDir != null) {
                if (rc.canMove(optimalDir)) {
                    rc.move(optimalDir);
                }
            }
        }
    }

    public static void healMove(RobotController rc, Direction optimalDir, MapLocation lowestCurrFriend,
            int lowestCurrFriendHealth, boolean attackerCanHeal) throws GameActionException {
        // Calculate what would be the lowest health of a friend after a movement.
        MapLocation aflowestCurrFriend = null;
        int aflowestCurrFriendHealth = Integer.MAX_VALUE;

        if (optimalDir != null) {
            RobotInfo[] affriends = rc.senseNearbyRobots(rc.getLocation().add(optimalDir),
                    GameConstants.HEAL_RADIUS_SQUARED, rc.getTeam());

            for (int i = affriends.length - 1; i >= 0; i--) {

                if (affriends[i].getHealth() < aflowestCurrFriendHealth) {
                    aflowestCurrFriendHealth = affriends[i].getHealth();
                    aflowestCurrFriend = affriends[i].getLocation();
                }

            }
        }

        // 1. if there is a friend within range and not one after you move,
        // while you can heal it, do so and move to the optimal dir
        // 2. else if there is a friend after moving and not one currently, move, then
        // heal
        // 3. else if, if both exist, choose move or heal order based on which would
        // yield heal to the lowest health friend.
        // 4. else if they both don't exist, move in optimal dir
        if (aflowestCurrFriend == null && lowestCurrFriend != null) {
            while (rc.canHeal(lowestCurrFriend) && attackerCanHeal) {
                rc.heal(lowestCurrFriend);
            }
            if (optimalDir != null) {
                if (rc.canMove(optimalDir)) {
                    rc.move(optimalDir);
                }
            }
        } else if (aflowestCurrFriend != null && lowestCurrFriend == null) {
            if (rc.canMove(optimalDir)) {
                rc.move(optimalDir);
            }
            while (rc.canHeal(aflowestCurrFriend) && attackerCanHeal) {
                rc.heal(aflowestCurrFriend);
            }
        } else if (aflowestCurrFriend != null && lowestCurrFriend != null) {
            if (aflowestCurrFriendHealth < lowestCurrFriendHealth) {
                if (rc.canMove(optimalDir)) {
                    rc.move(optimalDir);
                }
                while (rc.canHeal(aflowestCurrFriend) && attackerCanHeal) {
                    rc.heal(aflowestCurrFriend);
                }
            } else {
                while (lowestCurrFriend != null && rc.canHeal(lowestCurrFriend) && attackerCanHeal) {
                    rc.heal(lowestCurrFriend);
                }
                if (optimalDir != null) {
                    if (rc.canMove(optimalDir)) {
                        rc.move(optimalDir);
                    }
                }
            }
        } else {
            if (optimalDir != null) {
                if (rc.canMove(optimalDir)) {
                    rc.move(optimalDir);
                }
            }
        }
    }

    /**
     * Builders can train to level 6 by digging, so this method does this by digging
     * in a checkerboard pattern
     *
     * @param rc robotcontroller
     * @throws GameActionException
     */
    public static void trainToSixByDigging(RobotController rc)
            throws GameActionException {

        if (rc.isActionReady()) {
            MapInfo[] squaresWithinInteract = rc.senseNearbyMapInfos(GameConstants.INTERACT_RADIUS_SQUARED);
            // MapLocation fillableWater = null;
            boolean dug = false;

            for (int i = squaresWithinInteract.length - 1; i >= 0; i--) {
                // if (squaresWithinInteract[i].isWater()) {
                // fillableWater = squaresWithinInteract[i].getMapLocation();
                // }
                if (rc.canDig(squaresWithinInteract[i].getMapLocation())
                        && !doSidesHaveWater(rc, squaresWithinInteract[i].getMapLocation())
                        && !aNeighborIsADamOrWall(rc, squaresWithinInteract[i].getMapLocation())) {
                    rc.dig(squaresWithinInteract[i].getMapLocation());
                    dug = true;
                    break;
                }
            }
        }
        // if you can't find anywhere to dig, fill up a nearby water to dig on a future
        // turn (check if can do it this turn too)
        // if (!dug && fillableWater != null && rc.canFill(fillableWater)) {
        // rc.fill(fillableWater);
        // if (rc.canDig(fillableWater)) {
        // rc.dig(fillableWater);
        // }
        // }
    }

    public static Direction findOptimalCombatDir(RobotController rc, RobotInfo[] enemies, MapLocation lowestCurrHostile,
            MapLocation closestHostile,
            float averageDistFromEnemies,
            int numHostiles, int numFriendlies) throws GameActionException {
        // Calculate the best retreating direction and best attackign direction
        // Simulate moving to any of the four cardinal directions. Calculate the average
        // distance from all enemies.
        // Best Retreat direction is the direction that maximizes average Distance
        // Best Attacking direction is the direction that tries to keep troops at an
        // average distance
        // equal to the attack radius squared.

        Direction bestRetreat = null;
        Direction bestAttack = null;
        float bestRetreatDist = averageDistFromEnemies;
        float bestAttackDist = Integer.MAX_VALUE;

        Direction[] validCombatDirs = directions;
        for (int i = validCombatDirs.length - 1; i >= 0; i--) {
            MapLocation tempLoc = rc.getLocation().add(validCombatDirs[i]);

            if (rc.canSenseLocation(tempLoc) && rc.sensePassability(tempLoc)
                    && !rc.canSenseRobotAtLocation(tempLoc)) {
                float averageDist = (float) averageDistanceSquaredFrom(enemies, tempLoc);
                if (averageDist > 3 && averageDist < bestAttackDist) {
                    bestAttackDist = averageDist;
                    bestAttack = validCombatDirs[i];
                }

                if (averageDist > bestRetreatDist) {
                    bestRetreatDist = averageDist;
                    bestRetreat = validCombatDirs[i];
                }
            }
        }

        // Decide whether the bestAttack direction or bestRetreat direction is optimal
        // for the situation.

        // if health is less than half your health or the number of hostiles is larger
        // than 1,
        // go to best retreat dir. Otherwise, go to the best Attack dir.
        Direction optimalDir = null;

        // count damage you can take next round if you move bestAttack
        MapLocation advanceLoc = bestAttack == null ? rc.getLocation()
                : rc.getLocation().add(bestAttack);
        int dmg = 0;
        // int stunnedHostilesInVision = 0;
        for (int i = enemies.length; --i >= 0;) {
            RobotInfo enemy = enemies[i];
            // 10 r^2 is max dist where enemy is 1 move from attack range
            // if enemy is not in stunned cache
            // if (!Comms.stunnedEnemiesContains(enemy.getLocation())) {
            if (enemy.getLocation().distanceSquaredTo(advanceLoc) <= 10) {
                dmg += SkillType.ATTACK.skillEffect
                        + SkillType.ATTACK.getSkillEffect(enemy.getAttackLevel());
            }
            // } else {
            // stunnedHostilesInVision++;
            // }
        }
        // if there's a nearby hostile to shoot, always kite, othewise if you have to go
        // in, make sure that you have a clear numbers advantage.
        // best attack should either be the direction that puts you into range of one
        // enemy and the least number of other enemies OR
        // the direction that keeps you nearby, but not inrange of enemies.
        // Always rotates units so that the highest HP unit is up front
        // Goal: If there's a nearby hostile to shoot you should kite
        // Two different playstyles: Attacking and defending.
        // You're attacking if you have a two man advantage

        // Chase Direction : Direction to chase the enemy if you have an advantage
        // (minimizes enemies that can hit, while guarenteeing you can hit one).
        // boolean shouldKite = false;
        // boolean attacking = false;
        //
        // if (numHostiles + 2 <= numFriendlies + 1){
        // attacking = true;
        // }
        //
        // if (lowestCurrHostile != null) {
        // shouldKite = true;
        // }

        // if attacking, if your action is reacy, if there isn't a closest hostile,
        // chsse, otherwise,
        // if you can maintain a 10sq distance from the targets, shoot then go,
        // otherwise retreat.
        // if your action isn't ready, hide behind a friend with higher hp if it exists
        // otherwise maintain distance
        // if not attacking, if your action is ready, maintain distance.
        // if (attacking) {
        // if (rc.isActionReady()) {
        // if (bestProtectDir != null && rc.getHealth() == GameConstants.DEFAULT_HEALTH)
        // {
        // optimalDir = bestProtectDir;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestProtect: Friend: " +
        // protectFriend.toString());
        // }
        // } else if (bestHideDir != null && rc.getHealth() < 750) {
        // optimalDir = bestHideDir;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestHide: Friend: " +
        // hideBehindFriend.toString());
        // }
        // } else if (lowestCurrHostile == null) {
        // if (bestChase != null ) {
        // optimalDir = bestChase;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestChase: " + bestChase.toString());
        // }
        // } else if (bestAttack != null) {
        // optimalDir = bestAttack;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestAttack: " + bestAttack.toString());
        // }
        // }
        // } else {
        // if (bestMaintain != null) {
        // optimalDir = bestMaintain;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestMaintain: " + bestMaintain.toString());
        // }
        // } else if (bestRetreat != null) {
        // optimalDir = bestRetreat;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestRetreat: " + bestRetreat.toString());
        // }
        // }
        // }
        // } else if (bestHideDir != null) {
        // optimalDir = bestHideDir;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestHide: Friend: " +
        // hideBehindFriend.toString());
        // }
        // } else if (bestMaintain != null) {
        // optimalDir = bestMaintain;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestMaintain: " + bestMaintain.toString());
        // }
        // } else if (bestRetreat != null) {
        // optimalDir = bestRetreat;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestRetreat: " + bestRetreat.toString());
        // }
        // }
        // } else {
        // if (rc.isActionReady()) {
        // if (lowestCurrHostile == null) {
        // optimalDir = bestMaintain;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestMaintain: " + bestMaintain.toString());
        // }
        // } else {
        // if (bestMaintain != null) {
        // optimalDir = bestMaintain;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestMaintain: " + bestMaintain.toString());
        // }
        // } else if (bestRetreat != null) {
        // optimalDir = bestRetreat;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestRetreat: " + bestRetreat.toString());
        // }
        // }
        // }
        // } else if (bestHideDir != null) {
        // optimalDir = bestHideDir;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestHide: Friend: " +
        // hideBehindFriend.toString());
        // }
        // } else if (bestMaintain != null) {
        // optimalDir = bestMaintain;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestMaintain: " + bestMaintain.toString());
        // }
        // } else if (bestRetreat != null) {
        // optimalDir = bestRetreat;
        // if (optimalDir != null) {
        // rc.setIndicatorString("In combat bestRetreat: " + bestRetreat.toString());
        // }
        // }
        // }
        // Direction slideDir = null;
        // if (lowestCurrHostile != null &&
        // rc.getLocation().distanceSquaredTo(lowestCurrHostile) == 4) {
        // Direction toEnemy = rc.getLocation().directionTo(lowestCurrHostile);
        // Direction leftRotate = toEnemy.rotateLeft().rotateLeft();
        // Direction rightRotate = toEnemy.rotateRight().rotateRight();
        // MapLocation leftPos = rc.getLocation().add(leftRotate);
        // MapLocation rightPos = rc.getLocation().add(rightRotate);
        // int dmgLeft = 0;
        // int dmgRight = 0;
        // for (int i = enemies.length-1; i>=0 ;i--) {
        // RobotInfo enemy = enemies[i];
        // if (rc.canSenseLocation(leftPos) && rc.sensePassability(leftPos)
        // && !rc.canSenseRobotAtLocation(leftPos)) {
        // if (enemy.getLocation().distanceSquaredTo(leftPos) < 10) {
        // dmgLeft += SkillType.ATTACK.skillEffect
        // + SkillType.ATTACK.getSkillEffect(enemy.getAttackLevel());
        // }
        // }
        // if (rc.canSenseLocation(rightPos) && rc.sensePassability(rightPos)
        // && !rc.canSenseRobotAtLocation(rightPos)) {
        // if (enemy.getLocation().distanceSquaredTo(rightPos) < 10) {
        // dmgRight += SkillType.ATTACK.skillEffect
        // + SkillType.ATTACK.getSkillEffect(enemy.getAttackLevel());
        // }
        // }
        // }
        // if (dmgLeft < dmgRight && dmgLeft < rc.getHealth()) {
        // slideDir = leftRotate;
        // } else if (dmgRight < rc.getHealth()) {
        // slideDir = rightRotate;
        // }
        // }
        // Now: Two options: Go in or get out
        // if going in gets you killed, go out. If there are more enemies than friends,
        // go out. If you have no cooldown, go out.
        // Otherwise, go in.
        // changed here
        if (rc.getHealth() <= dmg || numHostiles - 2 /*- stunnedHostilesInVision*/ >= numFriendlies
                || (!rc.isActionReady() && !(rc.getActionCooldownTurns() / 10 == 1
                        && rc.getLocation().distanceSquaredTo(closestHostile) >= 17))
                || (rc.isActionReady() && lowestCurrHostile != null)) {
            // if (lowestCurrHostile == null &&
            // SkillType.ATTACK.getSkillEffect(rc.getLevel(SkillType.ATTACK)) <= dmgIfChill
            // && rc.isActionReady() && rc.getHealth() > dmgIfChill && numFriendlies + 1 >=
            // numHostiles + 2) {
            // optimalDir = null;
            // rc.setIndicatorString("Chill " + dmgIfChill);
            // } else {
            optimalDir = bestRetreat;
            if (optimalDir != null) {
                rc.setIndicatorString("In combat bestRetreat: " + bestRetreat.toString() + " " + dmg);
            }
        } else {
            optimalDir = bestAttack;
            if (optimalDir != null) {
                rc.setIndicatorString("In combat bestAttack: " + bestAttack.toString());
            }
        }
        return optimalDir;
    }

    public static double orthagonalDistanceOfP3RelativeToP2OnVectorP1P2(MapLocation P1, MapLocation P2,
            MapLocation P3) {
        // Set P1 as origin:
        double x2 = P2.x - P1.x;
        double x3 = P3.x - P1.x;
        double y2 = P2.y - P1.y;
        double y3 = P3.y - P1.y;

        double vectorP1P2Slope = y2 / x2;
        double vectorP1P2SlopeReciprocal = x2 / y2;

        if (vectorP1P2Slope == 0) {
            return Math.sqrt(Math.pow((x2 - x3), 2) + Math.pow(y2 - y3, 2));
        } else {
            double x3Projected = (y3 + vectorP1P2SlopeReciprocal * x3) / (vectorP1P2Slope + vectorP1P2SlopeReciprocal);
            double y3Projected = (vectorP1P2Slope * (y3 + vectorP1P2SlopeReciprocal * x3))
                    / (vectorP1P2Slope + vectorP1P2SlopeReciprocal);
            return Math.sqrt(Math.pow((x2 - x3Projected), 2) + Math.pow(y2 - y3Projected, 2));
        }

    }

    public static Direction findOptimalTrapKiteDir(RobotController rc, MapLocation closestEnemy, RobotInfo[] enemies,
            MapLocation nearestTrap) {
        Direction optimalDir = null;
        double optimalOrthoDist = Integer.MIN_VALUE;

        for (int j = directions.length - 1; j >= 0; j--) {
            if (rc.canMove(directions[j])
                    && rc.getLocation().distanceSquaredTo(closestEnemy) < GameConstants.VISION_RADIUS_SQUARED) {
                double totalOrthoDistanceToNearestTrap = 0;
                for (int i = enemies.length - 1; i >= 0; i--) {
                    if (enemies[i].getLocation().equals(nearestTrap)) {
                        totalOrthoDistanceToNearestTrap += Math
                                .sqrt(rc.getLocation().distanceSquaredTo(enemies[i].getLocation()));
                    } else {
                        totalOrthoDistanceToNearestTrap += orthagonalDistanceOfP3RelativeToP2OnVectorP1P2(nearestTrap,
                                enemies[i].getLocation(), rc.getLocation().add(directions[j]));
                    }
                }
                if (totalOrthoDistanceToNearestTrap > optimalOrthoDist) {
                    optimalOrthoDist = totalOrthoDistanceToNearestTrap;
                    optimalDir = directions[j];
                }
            }

        }

        return optimalDir;
    }

    public static Direction findOptimalPursuingStunDir(RobotController rc, RobotInfo[] enemies,
            float averageDistFromEnemies) throws GameActionException {
        Direction bestAttack = null;
        double bestAttackDist = averageDistFromEnemies;

        Direction[] validDirs = directions;
        for (int i = validDirs.length - 1; i >= 0; i--) {
            MapLocation tempLoc = rc.getLocation().add(validDirs[i]);

            if (rc.canSenseLocation(tempLoc) && rc.sensePassability(tempLoc)
                    && !rc.canSenseRobotAtLocation(tempLoc)) {
                float averageDist = (float) averageDistanceSquaredFrom(enemies, tempLoc);
                if (Math.abs(averageDist - GameConstants.ATTACK_RADIUS_SQUARED) < bestAttackDist) {
                    bestAttackDist = Math.abs(averageDist - GameConstants.ATTACK_RADIUS_SQUARED);
                    bestAttack = validDirs[i];
                }
            }
        }
        return bestAttack;
    }

    public static float averageDistanceSquaredFrom(RobotInfo[] bots, MapLocation location) {
        Integer[] allDistances = new Integer[bots.length];
        for (int j = bots.length - 1; j >= 0; j--) {
            if (bots[j] != null) {
                int tempDist = location.distanceSquaredTo(bots[j].getLocation());
                allDistances[j] = tempDist;
            }
        }
        int numInArray = 0;
        int sum = 0;
        for (int k = allDistances.length - 1; k >= 0; k--) {
            if (allDistances[k] != null) {
                sum += allDistances[k];
                numInArray++;
            }
        }
        float averageDist = (float) sum / numInArray;
        return averageDist;
    }

    public static boolean isInBounds(RobotController rc, MapLocation x) {
        if (x.x >= 0 && x.x < rc.getMapWidth() && x.y >= 0 && x.y < rc.getMapHeight()) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean doSidesHaveWater(RobotController rc, MapLocation x) throws GameActionException {
        MapLocation sideOne = new MapLocation(x.x + 1, x.y);
        MapLocation sideTwo = new MapLocation(x.x - 1, x.y);
        MapLocation sideThree = new MapLocation(x.x, x.y - 1);
        MapLocation sideFour = new MapLocation(x.x, x.y + 1);

        if (isInBounds(rc, sideOne) && rc.canSenseLocation(sideOne) && rc.senseMapInfo(sideOne).isWater()) {
            return true;
        } else if (isInBounds(rc, sideTwo) && rc.canSenseLocation(sideTwo) && rc.senseMapInfo(sideTwo).isWater()) {
            return true;
        } else if (isInBounds(rc, sideThree) && rc.canSenseLocation(sideThree)
                && rc.senseMapInfo(sideThree).isWater()) {
            return true;
        } else if (isInBounds(rc, sideFour) && rc.canSenseLocation(sideFour) && rc.senseMapInfo(sideFour).isWater()) {
            return true;
        } else {
            return false;
        }
    }

    public static int numSidesWithWater(RobotController rc, MapLocation x) throws GameActionException {
        MapLocation sideOne = new MapLocation(x.x + 1, x.y);
        MapLocation sideTwo = new MapLocation(x.x - 1, x.y);
        MapLocation sideThree = new MapLocation(x.x, x.y - 1);
        MapLocation sideFour = new MapLocation(x.x, x.y + 1);
        int numSidesWithWater = 0;

        if (isInBounds(rc, sideOne) && rc.senseMapInfo(sideOne).isWater()) {
            numSidesWithWater++;
        }
        if (isInBounds(rc, sideTwo) && rc.senseMapInfo(sideTwo).isWater()) {
            numSidesWithWater++;
        }
        if (isInBounds(rc, sideThree) && rc.senseMapInfo(sideThree).isWater()) {
            numSidesWithWater++;
        }
        if (isInBounds(rc, sideFour) && rc.senseMapInfo(sideFour).isWater()) {
            numSidesWithWater++;
        }
        return numSidesWithWater;
    }

    public static void clearTheWay(RobotController rc) throws GameActionException {
        // fill water with the most sides with water, but if you find a water with a
        // crumb, prioritize clearing that, else if you find a water with a
        // neighboring dam, prioritize clearing that
        if (rc.isActionReady()) {
            MapLocation fillLoc = null;
            int mostSides = 1;
            MapInfo[] nearbyInteractSquares = rc.senseNearbyMapInfos(GameConstants.INTERACT_RADIUS_SQUARED);
            for (int i = nearbyInteractSquares.length - 1; i >= 0; i--) {
                MapLocation buildLoc = nearbyInteractSquares[i].getMapLocation();
                if (isInBounds(rc, buildLoc) && nearbyInteractSquares[i].isWater() &&
                        rc.canFill(buildLoc)) {
                    if (nearbyInteractSquares[i].getCrumbs() != 0) {
                        fillLoc = buildLoc;
                        break;
                    } else if (aNeighborIsADamOrWall(rc, buildLoc)) {
                        fillLoc = buildLoc;
                        break;
                    } else if (numSidesWithWater(rc, buildLoc) > mostSides) {
                        fillLoc = buildLoc;
                        mostSides = numSidesWithWater(rc, buildLoc);
                    }
                }
            }

            // Check before moving if you can clear nearest water in the direction of
            // movement
            if (fillLoc != null && rc.canFill(fillLoc)) {
                rc.fill(fillLoc);
            }
        }
    }

    public static boolean areNeighborsOpen(RobotController rc, MapLocation x) throws GameActionException {
        MapInfo[] locations = rc.senseNearbyMapInfos(x, 1);
        for (int i = locations.length - 1; i >= 0; i--) {
            if (locations[i].isPassable() && !rc.isLocationOccupied(locations[i].getMapLocation())
                    && locations[i].getTeamTerritory().equals(rc.getTeam())) {
                return true;
            }
        }
        return false;
    }

    // is a neighbor of a location a dam or are there more than or equal to 7
    // impassibles nearby
    public static boolean aNeighborIsADamOrWall(RobotController rc, MapLocation x) throws GameActionException {
        MapInfo[] locations = rc.senseNearbyMapInfos(x, 4);
        int adjWallCounter = 0;
        for (int i = locations.length - 1; i >= 0; i--) {
            if (locations[i].isDam()) {
                return true;
            }
            if (locations[i].isWall() && x.distanceSquaredTo(locations[i].getMapLocation()) == 1) {
                adjWallCounter += 1;
            }
        }
        if (adjWallCounter >= 2) {
            return true;
        }
        return false;
    }

    // return the location of a friendly robot that has travel distance 1 or 2
    // closer to your closest spawn than you. also must have the action cooldown to
    // pick up the flag
    // returns MapLocation(-1, -1) if no such robot exists
    public static MapLocation findFlagRelay() throws GameActionException {
        MapLocation cacheDist1 = new MapLocation(-1, -1);
        int myTravelDistHome = Pathfinder.trueTravelDistance(rc.getLocation(), Info.closestSpawn);
        for (int i = Info.friendly_robots.length; --i >= 0;) {
            RobotInfo ri = Info.friendly_robots[i];

            int[] allyCD = Comms.getAllyCDs(ri.getID());
            // System.out.println(allyCD[0] + ", " + allyCD[1]);
            // if this ally action cooldown >= 10 and goes after me
            // or action cooldown >= 20 and goes before me, they won't be able to pick up
            // the flag.
            if (allyCD[0] >= 10 && !Comms.turnOrderBefore(rc.getID(), ri.getID()) ||
                    allyCD[0] >= 20 && Comms.turnOrderBefore(rc.getID(), ri.getID())) {
                continue;
            }

            MapLocation relay = ri.getLocation();
            int fTravelDistHome = Pathfinder.trueTravelDistance(relay, Info.closestSpawn);
            int fTravelDistMe = Pathfinder.trueTravelDistance(relay, rc.getLocation());

            if (fTravelDistMe == 2 && fTravelDistHome < myTravelDistHome &&
                    Pathfinder.trueTravelDistance(
                            Pathfinder.passableDirectionTowards(rc.getLocation().directionTo(relay)), relay) < 2) {

                // TODO: replace with BFSdist and take into account cooldowns and turn order
                return relay;
            } else if (fTravelDistMe == 1 && fTravelDistHome < myTravelDistHome) {
                cacheDist1 = relay;
            }
        }
        return cacheDist1;

    }

    public static Direction hideBehind(RobotController rc, MapLocation closestHostile, MapLocation hideBehindTarget)
            throws GameActionException {
        Direction[] validDirs = new Direction[5];
        validDirs[0] = rc.getLocation().directionTo(closestHostile).opposite();
        validDirs[1] = validDirs[0].rotateRight();
        validDirs[2] = validDirs[1].rotateRight();
        validDirs[3] = validDirs[0].rotateLeft();
        validDirs[4] = validDirs[3].rotateLeft();
        Direction optimalDir = null;
        double optimalDist = Integer.MIN_VALUE;
        for (int i = validDirs.length - 1; i >= 0; i--) {
            MapLocation tempLoc = rc.getLocation().add(validDirs[i]);
            if (rc.canSenseLocation(tempLoc) && rc.sensePassability(tempLoc)
                    && !rc.canSenseRobotAtLocation(tempLoc)) {
                double orthoDist = orthagonalDistanceOfP3RelativeToP2OnVectorP1P2(hideBehindTarget, closestHostile,
                        tempLoc);
                if (orthoDist > optimalDist) {
                    optimalDist = orthoDist;
                    optimalDir = validDirs[i];
                }
            }
        }
        return optimalDir;
    }

    public static Direction retreat(RobotController rc, RobotInfo[] enemies, MapLocation closestHostile)
            throws GameActionException {
        Direction[] validDirs = new Direction[5];
        validDirs[0] = rc.getLocation().directionTo(closestHostile).opposite();
        validDirs[1] = validDirs[0].rotateRight();
        validDirs[2] = validDirs[1].rotateRight();
        validDirs[3] = validDirs[0].rotateLeft();
        validDirs[4] = validDirs[3].rotateLeft();
        Direction optimalDir = null;
        double optimalDist = Integer.MIN_VALUE;
        for (int i = validDirs.length - 1; i >= 0; i--) {
            MapLocation tempLoc = rc.getLocation().add(validDirs[i]);
            if (rc.canSenseLocation(tempLoc) && rc.sensePassability(tempLoc)
                    && !rc.canSenseRobotAtLocation(tempLoc)) {
                float averageDist = (float) averageDistanceSquaredFrom(enemies, tempLoc);
                if (averageDist > optimalDist) {
                    optimalDist = averageDist;
                    optimalDir = validDirs[i];
                }
            }
        }
        return optimalDir;
    }

    public static Direction chase(RobotController rc, RobotInfo[] enemies, MapLocation hostile)
            throws GameActionException {
        Direction[] validDirs = new Direction[5];
        validDirs[0] = rc.getLocation().directionTo(hostile);
        validDirs[1] = validDirs[0].rotateRight();
        validDirs[2] = validDirs[1].rotateRight();
        validDirs[3] = validDirs[0].rotateLeft();
        validDirs[4] = validDirs[3].rotateLeft();
        Direction optimalDir = null;
        int optimalCanAttack = Integer.MAX_VALUE;
        for (int i = validDirs.length - 1; i >= 0; i--) {
            MapLocation tempLoc = rc.getLocation().add(validDirs[i]);
            if (rc.canSenseLocation(tempLoc) && rc.sensePassability(tempLoc)
                    && !rc.canSenseRobotAtLocation(tempLoc)
                    && rc.getLocation().distanceSquaredTo(tempLoc) <= GameConstants.ATTACK_RADIUS_SQUARED) {
                int canAttack = 0;
                for (int j = enemies.length - 1; j >= 0; j--) {
                    if (enemies[j].getLocation().distanceSquaredTo(tempLoc) <= GameConstants.ATTACK_RADIUS_SQUARED) {
                        canAttack++;
                    }
                }
                if (canAttack < optimalCanAttack) {
                    optimalDir = validDirs[i];
                    optimalCanAttack = canAttack;
                }
            }
        }
        return optimalDir;
    }

}
