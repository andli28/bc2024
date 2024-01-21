package test1;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
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
                    
    static boolean built = false;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        // System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // Spawn 1 robot
            if (!rc.isSpawned() && rc.readSharedArray(0) == 0){
                MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                // Pick a random spawn location to attempt spawning in.
                MapLocation randomLoc = spawnLocs[rng.nextInt(spawnLocs.length)];
                if (rc.canSpawn(randomLoc)) rc.spawn(randomLoc);                
                System.out.println("Spawned");
            }
            
            if (rc.isSpawned()){
                //write 1 to comms 1
                rc.writeSharedArray(0, 1);

                // set indicator to your move cooldown and your action cooldown
                rc.setIndicatorString("Move cooldown: " + rc.getMovementCooldownTurns() + ", Action cooldown: " + rc.getActionCooldownTurns());

                // If you can build on (14, 4), build a trap there.
                MapLocation goal = new MapLocation(14, 4);
                if (rc.canBuild(TrapType.EXPLOSIVE, goal)){
                    rc.build(TrapType.EXPLOSIVE, goal);
                    built = true;
                    // print out your team
                    System.out.println("Trap: " + rc.getTeam());
                    // move up
                    if (rc.canMove(Direction.WEST)) rc.move(Direction.WEST);
                }
                
                if (!built){                    
                    // Try to move towards (15, 15)
                    MapLocation myLocation = rc.getLocation();
                    MapLocation tgoal = new MapLocation(14, 3);
                    Direction toGoal = myLocation.directionTo(tgoal);
                    if (rc.canMove(toGoal)) rc.move(toGoal);
                }

                // if the turn is 250, build TrapType None while you can
                if (turnCount == 250){
                    while(rc.canBuild(TrapType.NONE, new MapLocation(13, 3))){
                        rc.build(TrapType.NONE, new MapLocation(13, 3));
                    }
                    
                }
                

            }

            // if turn count is 500, resign
            if (turnCount == 260){
                rc.resign();
            }

            

            turnCount += 1;  // We have now been alive for one more turn!                
            Clock.yield();

            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }
    
}
