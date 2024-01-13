package mirror2;

import battlecode.common.*;
import battlecode.world.Flag;

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

    /**
     * We will use this variable to count the number of turns this robot has been
     * alive.
     * You can use static variables like this to save any information you want. Keep
     * in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between
     * your robots.
     */
    static int turnCount = 0;

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

    /** Array containing all the possible movement directions. */
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

    // Default unit to scouting.
    static int role = SCOUTING;

    // set specializations
    static boolean BUILDERSPECIALIST = false;

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

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in
            // an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At
            // the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to
            // do.

            turnCount += 1; // We have now been alive for one more turn!

            // Resignation at 500 turns for testing purposes
            // if (turnCount == 500) {
            // rc.resign();
            // }

            // Try/catch blocks stop unhandled exceptions, which cause your robot to
            // explode.
            try {
                Comms.receive();
                // Default battlecode code for spawning:
                // Make sure you spawn your robot in before you attempt to take any actions!
                // Robots not spawned in do not have vision of any tiles and cannot perform any
                // actions.
                if (!rc.isSpawned()) {
                    MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                    // If theres a displaced flag, find the spawn location
                    // that has the smallest distance to the first displaced flag in the list and
                    // spawn there
                    // Else if there is a randomly sampled enemy, find the spawn location
                    // flag pair that has the smallest distance to the first sampled enemy in the
                    // list and spawn there
                    // Spawn anywhere you can for now(spawns 50 in turn 1)

                    MapLocation[] displacedFlags = Comms.getDisplacedAllyFlags();
                    MapLocation displacedFlag = null;
                    for (int i = displacedFlags.length - 1; i >= 0; i--) {
                        if (displacedFlags[i] != null) {
                            displacedFlag = displacedFlags[i];
                            break;
                        }
                    }

                    MapLocation[] sampledEnemies = Comms.getSampledEnemies();
                    MapLocation sampledEnemy = null;
                    for (int i = sampledEnemies.length - 1; i >= 0; i--) {
                        if (sampledEnemies[i] != null) {
                            sampledEnemy = sampledEnemies[i];
                            break;
                        }
                    }
                    haveSeenCombat = false;

                    if (displacedFlag != null) {
                        int distToClosestToDisplacedFlag = Integer.MAX_VALUE;
                        MapLocation closestToDisplacedFlag = null;
                        for (int i = spawnLocs.length - 1; i >= 0; i--) {
                            if (spawnLocs[i].distanceSquaredTo(displacedFlag) < distToClosestToDisplacedFlag) {
                                distToClosestToDisplacedFlag = spawnLocs[i].distanceSquaredTo(displacedFlag);
                                closestToDisplacedFlag = spawnLocs[i];
                            }
                        }
                        if (rc.canSpawn(closestToDisplacedFlag)) {
                            rc.spawn(closestToDisplacedFlag);
                        }
                    } else if (sampledEnemy != null) {
                        int distToClosestToSampledEnemy = Integer.MAX_VALUE;
                        MapLocation closestToSampledEnemy = null;
                        for (int i = spawnLocs.length - 1; i >= 0; i--) {
                            if (spawnLocs[i].distanceSquaredTo(sampledEnemy) < distToClosestToSampledEnemy) {
                                distToClosestToSampledEnemy = spawnLocs[i].distanceSquaredTo(sampledEnemy);
                                closestToSampledEnemy = spawnLocs[i];
                            }
                        }
                        if (rc.canSpawn(closestToSampledEnemy)) {
                            rc.spawn(closestToSampledEnemy);
                        }
                    } else {
                        for (int i = spawnLocs.length; --i >= 0;) {
                            MapLocation loc = spawnLocs[i];
                            if (rc.canSpawn(loc))
                                rc.spawn(loc);
                        }
                    }
                }

                if (rc.isSpawned()) {

                    // decide if this person should be a builder (if shortId <3)
                    if (turnCount > 1 && Comms.shortId < 3) {
                        BUILDERSPECIALIST = true;
                    }

                    // 750 Turn upgrade + 1500 turn upgrade
                    if (turnCount == GameConstants.GLOBAL_UPGRADE_ROUNDS && rc.canBuyGlobal(GlobalUpgrade.ACTION)) {
                        rc.buyGlobal(GlobalUpgrade.ACTION);
                    } else if (turnCount == 2 * GameConstants.GLOBAL_UPGRADE_ROUNDS
                            && rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
                        rc.buyGlobal(GlobalUpgrade.HEALING);
                    }

                    // Information about the closest displaced flag + distance to that MapLocation
                    MapLocation closestDisplacedFlag = Comms.closestDisplacedAllyFlag();

                    int distToClosestDisplacedFlag = Integer.MAX_VALUE;
                    if (closestDisplacedFlag != null) {
                        distToClosestDisplacedFlag = rc.getLocation().distanceSquaredTo(closestDisplacedFlag);
                    }

                    // MapInfo Counting: Counting Traps, Water, etc
                    MapLocation nearestStunTrap = null;
                    MapLocation nearestExplosiveTrap = null;
                    MapLocation nearestWaterTrap = null;
                    MapLocation nearestWater = null;

                    int lowestDistToStunTrap = Integer.MAX_VALUE;
                    int lowestDistToExplosiveTrap = Integer.MAX_VALUE;
                    int lowestDistToWaterTrap = Integer.MAX_VALUE;
                    int lowestDistToWater = Integer.MAX_VALUE;

                    MapInfo[] nearbyMap = rc.senseNearbyMapInfos();

                    for (int i = nearbyMap.length - 1; i >= 0; i--) {
                        MapInfo singleMap = nearbyMap[i];
                        int distToSingleMap = rc.getLocation().distanceSquaredTo(singleMap.getMapLocation());
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
                    }

                    // if you were randomly chosen as a builder specialist && turncount is between
                    // 75 and max setup rounds
                    // , train by digging until you have 30 exp

                    if (BUILDERSPECIALIST && rc.getExperience(SkillType.BUILD) < 30 && turnCount > 25) {
                        trainToSixByDigging(rc, nearestWater, lowestDistToWater);
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

                    // Calculate the average distance from all enemies.
                    Integer[] allDistancesFromEnemies = new Integer[enemies.length];
                    for (int j = enemies.length - 1; j >= 0; j--) {
                        allDistancesFromEnemies[j] = rc.getLocation().distanceSquaredTo(enemies[j].getLocation());
                    }
                    int sumOfDistances = 0;
                    for (int k = allDistancesFromEnemies.length - 1; k >= 0; k--) {
                        sumOfDistances += allDistancesFromEnemies[k];
                    }
                    float averageDistFromEnemies = (float) sumOfDistances / allDistancesFromEnemies.length;

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

                    // setting default threshold for portion of health before designated wounded as
                    // .4, but .9 for builderSpecialist
                    double woundedRetreatThreshold = .4;
                    if (BUILDERSPECIALIST) {
                        woundedRetreatThreshold = .9;
                    }

                    //when building explosive or stun traps, this is the preferred distance when
                    // building them away from one another
                    int explosiveTrapPreferredDist = 10;
                    int stunTrapPreferredDist =7;

                    // Role Delegation
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
                        // } else if (enemies.length != 0
                        // && rc.getHealth() < GameConstants.DEFAULT_HEALTH * woundedRetreatThreshold) {
                        // role = WOUNDED;
                        // rc.setIndicatorString("Wounded");
                    } else if (rc.getExperience(SkillType.BUILD) >= 30 && rc.getCrumbs() > 250 && enemies.length != 0) {
                        role = BUILDING;
                        rc.setIndicatorString("Building");
                    } else if (enemies.length != 0) {
                        role = INCOMBAT;
                        haveSeenCombat = true;
                        rc.setIndicatorString("In combat");
                    } else if (numFlagsNearbyNotPickedUp != 0) {
                        role = CAPTURING;
                        rc.setIndicatorString("Capturing");
                    } else if (closestDisplacedFlag != null) {
                        role = DEFENDING;
                        rc.setIndicatorString("Defending");
                    } else if (lowestCurrFriendlySeenHealth < GameConstants.DEFAULT_HEALTH) {
                        role = HEALING;
                        rc.setIndicatorString("Healing");
                    } else {
                        role = SCOUTING;
                        rc.setIndicatorString("Scouting");
                    }

                    if (role == SCOUTING) {
                        // FLOW OF LOGIC:
                        // 1. Randomly target a certain point on the map and when the target has been
                        // reached,
                        // designate a new target (ie. random scouting).
                        // 2. IF a breadcrumb is seen within vision radius, go to that square, otherwise
                        // continue
                        // random scouting
                        // 3. IF nearby water is not null, go to that square and clear it.

                        // Get the location of all nearby crumbs
                        MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(GameConstants.VISION_RADIUS_SQUARED);
                        boolean activelyPursuingCrumb = false;

                        // If nearbyCrumbs is not empty, go to the crumb that is first in the list,
                        // else,
                        // continue to random target. If random target has not been chosen, or the bot
                        // is
                        // at the random target, generate a new random tgt.
                        // the else if is to account for the case where a crumb has been eaten before
                        // this unit has had a chance to pick it up, so add a case to prevent redudant
                        // persuit of a crumb that is not there
                        if (nearbyCrumbs.length != 0) {
                            for (int i = nearbyCrumbs.length - 1; i >= 0; i--) {
                                if (rc.sensePassability(nearbyCrumbs[i])) {
                                    tgtLocation = nearbyCrumbs[i];
                                    activelyPursuingCrumb = true;
                                    lastTurnPursingCrumb = true;
                                    lastTurnPursingWater = false;
                                }
                            }
                        }

                        // If we are not actively pursuing a crumb, check if we can clear/pursue a
                        // water tile, otherwise generate a random target.
                        if (!activelyPursuingCrumb) {
                            if (nearestWater != null) {
                                if (lowestDistToWater <= GameConstants.INTERACT_RADIUS_SQUARED
                                        && rc.canFill(nearestWater)) {
                                    rc.fill(nearestWater);
                                } else {
                                    tgtLocation = nearestWater;
                                    lastTurnPursingWater = true;
                                    lastTurnPursingCrumb = false;
                                }
                            }
                            // Generating a random target:
                            // if tgtLocation is null, the current location equals the target location,
                            // or the target location can be sensed and is impassible, or turnsNotReachedTgt
                            // > 50,
                            // generate a new random target location.
                            // Note: the bounds are 3 to the bounds-3 because the robots have a vision
                            // radius of sqrt(20), so
                            // they only need to get within 3 units of each edge to see the full edge of the
                            // map, including the corners.
                            else if (tgtLocation == null || rc.getLocation().equals(tgtLocation) ||
                                    (rc.canSenseLocation(tgtLocation) && !rc.sensePassability(tgtLocation))
                                    || turnsNotReachedTgt > 50 || lastTurnPursingCrumb || lastTurnPursingWater) {
                                tgtLocation = generateRandomMapLocation(3, rc.getMapWidth() - 3,
                                        3, rc.getMapHeight() - 3);
                                lastTurnPursingCrumb = false;
                                lastTurnPursingWater = false;
                            }
                        }

                        // Initialize Direction to Move
                        Direction dir = Pathfinder.pathfind(rc.getLocation(), tgtLocation);
                        // rc.setIndicatorString(tgtLocation.toString());

                        // If can move to dir, move.
                        if (rc.canMove(dir)) {
                            rc.move(dir);
                        }

                        rc.setIndicatorString("Scouting: Tgt: " + tgtLocation.toString());

                    } else if (role == INCOMBAT) {

                        Direction optimalDir = findOptimalCombatDir(rc,enemies, averageDistFromEnemies, woundedRetreatThreshold, numHostiles, numFriendlies);
                        attackMove(rc, optimalDir, lowestCurrHostile, lowestCurrHostileHealth);

                    } else if (role == BUILDING) {

                        if (closestHostile != null) {
                            layTrapWithinRangeOfEnemy(rc, nearestExplosiveTrap, nearestStunTrap, closestHostile,
                                    10, 7, 9);
                        } else {
                            layTrap(rc, nearestExplosiveTrap, nearestStunTrap, 10, 7);
                        }

                        Direction optimalDir = findOptimalCombatDir(rc,enemies, averageDistFromEnemies, woundedRetreatThreshold, numHostiles, numFriendlies);
                        attackMove(rc, optimalDir, lowestCurrHostile, lowestCurrHostileHealth);

                    } else if (role == CAPTURING) {
                        // If you can pick up the flag, pick it up, otherwise calculate the nearest
                        // enemy flag, and go to it
                        if (rc.canPickupFlag(rc.getLocation())) {
                            rc.pickupFlag(rc.getLocation());
                        } else {
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
                            Direction dir = Pathfinder.pathfind(rc.getLocation(), closestFlag);
                            if (rc.canMove(dir)) {
                                rc.move(dir);
                            }
                        }

                    } else if (role == RETURNING) {
                        // If we are holding an enemy flag, singularly focus on moving towards
                        // an ally spawn zone to capture it! We use the check roundNum >= SETUP_ROUNDS
                        // to make sure setup phase has ended.
                        if (rc.hasFlag() && rc.getRoundNum() >= GameConstants.SETUP_ROUNDS) {
                            MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                            // find closest spawnLoc:
                            MapLocation closestSpawn = null;
                            int closestSpawnDist = Integer.MAX_VALUE;
                            for (int i = spawnLocs.length - 1; i >= 0; i--) {
                                int distSqToSpawn = rc.getLocation().distanceSquaredTo(spawnLocs[i]);
                                if (distSqToSpawn < closestSpawnDist) {
                                    closestSpawnDist = distSqToSpawn;
                                    closestSpawn = spawnLocs[i];
                                }
                            }
                            Direction dir = Pathfinder.pathfind(rc.getLocation(), closestSpawn);                            
                            rc.setIndicatorString("returning: " + closestSpawn.toString());
                            if (rc.canMove(dir)) {
                                rc.move(dir);
                            }
                        }
                    } else if (role == DEFENDING) {

                        Direction dir = Pathfinder.pathfind(rc.getLocation(), closestDisplacedFlag);
                        if (rc.canMove(dir)) {
                            rc.move(dir);
                        }

                        rc.setIndicatorString("Defending" + tgtLocation.toString());
                    } else if (role == HEALING) {

                        Direction dir = Pathfinder.pathfind(rc.getLocation(), lowestCurrFriendlySeen);
                        if (rc.canMove(dir)) {
                            rc.move(dir);
                        }

                    } else if (role == WOUNDED) {

                        // enable units to go in any direction.
                        // optimal direction is prioritized by it being the furthest from enemies
                        Direction bestRetreat = null;
                        float bestRetreatDist = averageDistFromEnemies;

                        for (int i = directions.length - 1; i >= 0; i--) {
                            MapLocation tempLoc = rc.getLocation().add(directions[i]);

                            if (rc.canSenseLocation(tempLoc) && rc.sensePassability(tempLoc)
                                    && !rc.canSenseRobotAtLocation(tempLoc)) {
                                Integer[] allDistances = new Integer[enemies.length];
                                for (int j = enemies.length - 1; j >= 0; j--) {
                                    if (enemies[j] != null) {
                                        int tempDist = tempLoc.distanceSquaredTo(enemies[j].getLocation());
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
                                if (averageDist > bestRetreatDist) {
                                    bestRetreatDist = averageDist;
                                    bestRetreat = directions[i];
                                }
                            }
                        }

                        attackMove(rc, bestRetreat, lowestCurrHostile, lowestCurrHostileHealth);

                        // if you still have cooldown because you didn't attack, and you're a builder
                        // specialist, lay traps
                        if (BUILDERSPECIALIST) {
                            if (closestHostile != null) {
                                layTrapWithinRangeOfEnemy(rc, nearestExplosiveTrap, nearestStunTrap, closestHostile,
                                        10, 7,9);
                            } else {
                                layTrap(rc, nearestExplosiveTrap, nearestStunTrap,
                                        10, 7);
                            }
                        }
                    }

                    while (lowestCurrFriendly != null && rc.canHeal(lowestCurrFriendly)) {
                        rc.heal(lowestCurrFriendly);
                    }
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
                }
                if (rc.getRoundNum() == 1) {
                    Comms.initialize();
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
     * @param rc                   robotcontroller
     * @param nearestExplosiveTrap maplocation of nearest explosive trap
     * @param nearestStunTrap      maplocation of nearest stun trap
     * @param explosiveTrapPreferredDist distance an explosive trap should be away from another
     * @param stunTrapPreferredDist distance a stun trap should be away from another
     * @throws GameActionException
     */
    public static void layTrap(RobotController rc, MapLocation nearestExplosiveTrap, MapLocation nearestStunTrap,
                               int explosiveTrapPreferredDist, int stunTrapPreferredDist)
            throws GameActionException {
        // Iterate through all building directions, and go through the following logic:
        // 1 . If there are no nearby Explosive traps, build one,
        // 2. Else if there are no nearby Stun Traps, build one.
        // 3. Else if there are both, prioritize builiding another explosive trap that
        // is at least 10 sq units away from the first one
        // 4. IF you can't build the explosive trap, but can build the stun trap at
        // least 7 sq units away from the first one, do so.
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
                if (buildLoc.distanceSquaredTo(nearestExplosiveTrap) > explosiveTrapPreferredDist
                        && rc.canBuild(TrapType.EXPLOSIVE, buildLoc)) {
                    rc.build(TrapType.EXPLOSIVE, buildLoc);
                    break;
                } else if (buildLoc.distanceSquaredTo(nearestStunTrap) > stunTrapPreferredDist
                        && rc.canBuild(TrapType.STUN, buildLoc)) {
                    rc.build(TrapType.STUN, buildLoc);
                    break;
                }
            }
        }
    }

    /**
     * Building a trap (taking into account trap spacing) within the given range of
     * the closest enemy
     * 
     * @param rc                   robotcontroller
     * @param nearestExplosiveTrap maplocation of nearest explosive trap
     * @param nearestStunTrap      maplocation of nearest stun trap
     * @param closestEnemy         maplocation of nearest enemy trap
     * @param explosiveTrapPreferredDist distance an explosive trap should be away from another
     * @param stunTrapPreferredDist distance a stun trap should be away from another
     * @param buildThreshold       max distance squared where a trap should be build
     *                             from the enemy
     * @throws GameActionException
     */
    public static void layTrapWithinRangeOfEnemy(RobotController rc, MapLocation nearestExplosiveTrap,
            MapLocation nearestStunTrap, MapLocation closestEnemy, int explosiveTrapPreferredDist, int stunTrapPreferredDist,
                                                 int buildThreshold) throws GameActionException {
        // Iterate through all building directions, and go through the following logic:
        // 1 . If there are no nearby Explosive traps, build one,
        // 2. Else if there are no nearby Stun Traps, build one.
        // 3. Else if there are both, prioritize builiding another explosive trap that
        // is at least 10 sq units away from the first one
        // 4. IF you can't build the explosive trap, but can build the stun trap at
        // least 7 sq units away from the first one, do so.
        for (int i = Direction.allDirections().length - 1; i >= 0; i--) {
            MapLocation buildLoc = rc.getLocation().add(Direction.allDirections()[i]);
            if (buildLoc.distanceSquaredTo(closestEnemy) <= buildThreshold) {
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
                    if (buildLoc.distanceSquaredTo(nearestExplosiveTrap) > explosiveTrapPreferredDist
                            && rc.canBuild(TrapType.EXPLOSIVE, buildLoc)) {
                        rc.build(TrapType.EXPLOSIVE, buildLoc);
                        break;
                    } else if (buildLoc.distanceSquaredTo(nearestStunTrap) > stunTrapPreferredDist
                            && rc.canBuild(TrapType.STUN, buildLoc)) {
                        rc.build(TrapType.STUN, buildLoc);
                        break;
                    }
                }
            }
        }
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

    /**
     * Builders can train to level 6 by digging, so this method does this by filling
     * nearby water and creating holes
     * 
     * @param rc                robotcontroller
     * @param nearestWater      nearestwater maplocation
     * @param lowestDistToWater distance from nearest water
     * @throws GameActionException
     */
    public static void trainToSixByDigging(RobotController rc, MapLocation nearestWater, int lowestDistToWater)
            throws GameActionException {

        if (nearestWater != null) {
            if (lowestDistToWater <= GameConstants.INTERACT_RADIUS_SQUARED
                    && rc.canFill(nearestWater)) {
                rc.fill(nearestWater);
            }
        } else {
            for (int i = directions.length - 1; i >= 0; i--) {
                MapLocation addedLoc = rc.getLocation().add(directions[i]);
                if (rc.canDig(addedLoc)) {
                    rc.dig(addedLoc);
                    break;
                }
            }
        }
    }

    public static Direction findOptimalCombatDir(RobotController rc, RobotInfo[] enemies,
                                                 float averageDistFromEnemies, double woundedRetreatThreshold,
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
        float bestAttackDist = averageDistFromEnemies;

        Direction[] validCombatDirs = rc.getHealth() < GameConstants.DEFAULT_HEALTH
                * woundedRetreatThreshold ? directions : allCombatDirs;
        for (int i = validCombatDirs.length - 1; i >= 0; i--) {
            MapLocation tempLoc = rc.getLocation().add(validCombatDirs[i]);

            if (rc.canSenseLocation(tempLoc) && rc.sensePassability(tempLoc)
                    && !rc.canSenseRobotAtLocation(tempLoc)) {
                Integer[] allDistances = new Integer[enemies.length];
                for (int j = enemies.length - 1; j >= 0; j--) {
                    if (enemies[j] != null) {
                        int tempDist = tempLoc.distanceSquaredTo(enemies[j].getLocation());
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
                if (Math.abs(averageDist - GameConstants.ATTACK_RADIUS_SQUARED) < bestAttackDist) {
                    bestAttackDist = Math.abs(averageDist - GameConstants.ATTACK_RADIUS_SQUARED);
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

        // at what decimal place of the max health will you retreat? Default .5, for a
        // builder specialist, .9.
        double inCombatRetreatThreshold = .5;
        if (BUILDERSPECIALIST) {
            inCombatRetreatThreshold = .9;
        }

        // count damage you can take next round if you move bestAttack
        MapLocation advanceLoc = bestAttack == null ? rc.getLocation()
                : rc.getLocation().add(bestAttack);
        int dmg = 0;
        for (int i = enemies.length; --i >= 0;) {
            RobotInfo enemy = enemies[i];
            // 10 r^2 is max dist where enemy is 1 move from attack range
            if (enemy.getLocation().distanceSquaredTo(advanceLoc) <= 10) {
                dmg += SkillType.ATTACK.skillEffect
                        + SkillType.ATTACK.getSkillEffect(enemy.getAttackLevel());
            }
        }

        if (rc.getHealth() < dmg || numHostiles > numFriendlies || !rc.isActionReady()) {
            optimalDir = bestRetreat;
        } else {
            optimalDir = bestAttack;
        }
        return optimalDir;
    }
}
