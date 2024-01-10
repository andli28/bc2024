package mainbot;

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
    static final Random rng = new Random(6147);

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

    // Designating Constants:
    static MapLocation tgtLocation = null;
    static int turnsNotReachedTgt = 0;
    static boolean lastTurnCrumbSearch = false;
    static boolean haveSeenCombat = false;

    // Delegating Roles:
    // 1. Scouting - base role for units. Purpose: to explore the map, gather
    // information, and gather breadcrumbs.
    // 2. InCombat - role for when an enemy unit is seen. Purpose: win the
    // engagement, or decide to retreat.
    static final int SCOUTING = 0;
    static final int INCOMBAT = 1;
    static final int BUILDING = 2;

    // Default unit to scouting.
    static int role = SCOUTING;

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
        rc.setIndicatorString("Hello world!");

        Pathfinder.rc = rc;

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in
            // an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At
            // the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to
            // do.

            turnCount += 1; // We have now been alive for one more turn!

            // Resignation at 500 turns for testing purposes
//            if (turnCount == 500) {
//                rc.resign();
//            }

            // Try/catch blocks stop unhandled exceptions, which cause your robot to
            // explode.
            try {
                // Default battlecode code for spawning:
                // Make sure you spawn your robot in before you attempt to take any actions!
                // Robots not spawned in do not have vision of any tiles and cannot perform any
                // actions.
                if (!rc.isSpawned()) {
                    MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                    // Pick a random spawn location to attempt spawning in.
                    MapLocation randomLoc = spawnLocs[rng.nextInt(spawnLocs.length)];
                    haveSeenCombat = false;
                    if (rc.canSpawn(randomLoc))
                        rc.spawn(randomLoc);
                } else {

                    //Enemy Counting, finding number of hostiles, number of hostiles in range, and the nearby hostile with the lowest HP
                    RobotInfo[] enemies = rc.senseNearbyRobots(rc.getLocation(), GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent());
                    int numHostiles = 0;
                    int numHostilesIR = 0;
                    MapLocation lowestCurrHostile = null;
                    int lowestCurrHostileHealth = Integer.MAX_VALUE;
                    if (enemies.length != 0) {
                        for (int i = enemies.length - 1; i >= 0; i--) {
                            numHostiles++;
                            if (enemies[i].getLocation().distanceSquaredTo(rc.getLocation()) <= GameConstants.ATTACK_RADIUS_SQUARED) {
                                numHostilesIR++;
                            }
                            if (rc.getLocation().distanceSquaredTo(enemies[i].getLocation()) <= GameConstants.ATTACK_RADIUS_SQUARED
                                    && enemies[i].getHealth() < lowestCurrHostileHealth) {
                                lowestCurrHostileHealth = enemies[i].getHealth();
                                lowestCurrHostile = enemies[i].getLocation();
                            }
                        }
                    }

                    //Friendly Counting, finding number of friendlies, number of friends in range, and nearby friend with lowest HP
                    RobotInfo[] friendlies = rc.senseNearbyRobots(rc.getLocation(), GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());
                    int numFriendlies = 0;
                    int numFriendliesIR = 0;
                    MapLocation lowestCurrFriendly = null;
                    int lowestCurrFriendlyHealth = Integer.MAX_VALUE;
                    if (friendlies.length != 0) {
                        for (int i = friendlies.length - 1; i >= 0; i--) {
                            numFriendlies++;
                            if (friendlies[i].getLocation().distanceSquaredTo(rc.getLocation()) <= GameConstants.HEAL_RADIUS_SQUARED) {
                                numFriendliesIR++;
                            }
                            if (rc.getLocation().distanceSquaredTo(friendlies[i].getLocation()) <= GameConstants.HEAL_RADIUS_SQUARED
                                    && friendlies[i].getHealth() < lowestCurrHostileHealth) {
                                lowestCurrFriendlyHealth = friendlies[i].getHealth();
                                lowestCurrFriendly = friendlies[i].getLocation();
                            }
                        }
                    }

                    //Role Delegation
                    // If there is a nearby enemy, you're in combat. Else if you have more than 250
                    // crumbs and you've seen combat before go into build mode. Otherwise, scout.
                    if (enemies.length != 0) {
                        role = INCOMBAT;
                        haveSeenCombat = true;
                        rc.setIndicatorString("In combat");
                    } else if (rc.getCrumbs() > 250 && haveSeenCombat) {
                        role = BUILDING;
                        rc.setIndicatorString("Building");
                    } else {
                        role = SCOUTING;
                        rc.setIndicatorString("Scouting");
                    }

                    if (role == SCOUTING) {
                        // FLOW OF LOGIC:
                        // 1. Randomly target a certain point on the map and when the target has been
                        // reached,
                        // designate a new target (ie. random scouting). IF a divider is spotted,
                        // automatically
                        // change targets to avoid stalling
                        // 2. IF a breadcrumb is seen within vision radius, go to that square, otherwise
                        // continue
                        // random scouting

                        // Generating a random target:
                        // if tgtLocation is null, the current location equals the target location,
                        // or the target location can be sensed and is impassible, or turnsNotReachedTgt > 50,
                        // generate a new random target location.
                        // Note: the bounds are 3 to the bounds-3 because the robots have a vision
                        // radius of sqrt(20), so
                        // they only need to get within 3 units of each edge to see the full edge of the
                        // map, including the corners.
                        if (tgtLocation == null || rc.getLocation().equals(tgtLocation) ||
                                (rc.canSenseLocation(tgtLocation) && !rc.sensePassability(tgtLocation))
                                || turnsNotReachedTgt > 50) {
                            tgtLocation = generateRandomMapLocation(3, rc.getMapWidth() - 3,
                                    3, rc.getMapHeight() - 3);
                            lastTurnCrumbSearch = false;
                        }

                        // Get the location of all nearby crumbs
                        MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(GameConstants.VISION_RADIUS_SQUARED);

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
                                    lastTurnCrumbSearch = true;
                                }
                            }
                        } else if (lastTurnCrumbSearch) {
                            tgtLocation = generateRandomMapLocation(3, rc.getMapWidth() - 3,
                                    3, rc.getMapHeight() - 3);
                            lastTurnCrumbSearch = false;
                        }

                        // Initialize Direction to Move
                        Direction dir = Pathfinder.pathfind(rc.getLocation(), tgtLocation);
                        //rc.setIndicatorString(tgtLocation.toString());

                        // If can move to dir, move.
                        if (rc.canMove(dir)) {
                            rc.move(dir);
                        }

                    } else if (role == INCOMBAT) {
                        // If there is a nearby hostile, attack the one with the lowest HP
                        if (lowestCurrHostile != null && rc.canAttack(lowestCurrHostile)) {
                            rc.attack(lowestCurrHostile);
                        }

                    } else if (role == BUILDING) {
                        //Iterate through all building directions, and build a trap there if you can.
                        for (int i = Direction.allDirections().length - 1; i >= 0 ; i--) {
                            if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation().add(Direction.allDirections()[i]))) {
                                rc.build(TrapType.EXPLOSIVE, rc.getLocation().add(Direction.allDirections()[i]));
                                break;
                            }
                        }
                    }

                    if (rc.isActionReady() && lowestCurrFriendly != null && rc.canHeal(lowestCurrFriendly)) {
                        rc.heal(lowestCurrFriendly);
                    }
//                    // default battlecode code:
//                    else {
//                        if (rc.canPickupFlag(rc.getLocation())) {
//                            rc.pickupFlag(rc.getLocation());
//                        }
//                        // If we are holding an enemy flag, singularly focus on moving towards
//                        // an ally spawn zone to capture it! We use the check roundNum >= SETUP_ROUNDS
//                        // to make sure setup phase has ended.
//                        if (rc.hasFlag() && rc.getRoundNum() >= GameConstants.SETUP_ROUNDS) {
//                            MapLocation[] spawnLocs = rc.getAllySpawnLocations();
//                            MapLocation firstLoc = spawnLocs[0];
//                            Direction dir = rc.getLocation().directionTo(firstLoc);
//                            if (rc.canMove(dir))
//                                rc.move(dir);
//                        }
//                        // Move and attack randomly if no objective.
//                        Direction dir = Pathfinder.pathfind(rc.getLocation(), new MapLocation(29, 0));
//                        MapLocation nextLoc = rc.getLocation().add(dir);
//                        if (rc.canMove(dir)) {
//                            rc.move(dir);
//                        } else if (rc.canAttack(nextLoc)) {
//                            rc.attack(nextLoc);
//                            System.out.println("Take that! Damaged an enemy that was in our way!");
//                        }
//
//                        // Rarely attempt placing traps behind the robot.
//                        MapLocation prevLoc = rc.getLocation().subtract(dir);
//                        if (rc.canBuild(TrapType.EXPLOSIVE, prevLoc) && rng.nextInt() % 37 == 1)
//                            rc.build(TrapType.EXPLOSIVE, prevLoc);
//                        // We can also move our code into different methods or classes to better
//                        // organize it!
//                        updateEnemyRobots(rc);

                }

            } catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You
                // should
                // handle GameActionExceptions judiciously, in case unexpected events occur in
                // the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
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
}
