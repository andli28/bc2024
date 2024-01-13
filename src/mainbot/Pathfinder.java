package mainbot;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;

// buh g rotation enum
enum Rot {
    LEFT,
    RIGHT,
    NONE
}

/**
 * favorite apex legend?
 */
public class Pathfinder {
    public static RobotController rc;
    public static Rot buhgDir = Rot.NONE;
    public static Direction lastBuhgDir = Direction.CENTER;
    public static int initBlockDist = 9999;
    public static int turnsBuhgging = 0;

    // cosine similarity for 2 2d vecs
    public static float cosSim(int dx1, int dy1, int dx2, int dy2) {
        return (dx1 * dx2 + dy1 * dy2) / ((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 + dy2));
    }

    // does not explicitly check movement cd but does canmove checks
    public static Direction pathfind(MapLocation src, MapLocation tgt) throws GameActionException {
        if (!rc.isMovementReady() || src.equals(tgt))
            return Direction.CENTER;
        // some variant buh g with wall avoidance
        Direction dirTo = src.directionTo(tgt);

        // if direct is blocked
        boolean directPassable = rc.senseMapInfo(src.add(dirTo)).isPassable();

        // direct greedily(8 dir) if can until impassible 1 tile no los, then buhg
        if (directPassable && buhgDir == Rot.NONE) {
            Direction bestDir = Direction.CENTER;
            int minDist = 9999;
            for (Direction d : Direction.allDirections()) {
                MapLocation curr = src.add(d);
                if (!rc.canMove(d) && d != Direction.CENTER)
                    continue;
                int currDist = curr.distanceSquaredTo(tgt);
                if (minDist > currDist) {
                    minDist = currDist;
                    bestDir = d;
                }
            }
            return bestDir;
        }

        // tldr a better first buhg dir guess system
        if (buhgDir == Rot.NONE) {
            // if blocked and no prev buhg choose a buhg dir depending on some ob bounds
            int minDist = 9999;
            MapLocation minBound = null;

            // scan nearby impassibles, only looking at ones that intersect with max vision
            // ring
            MapInfo[] nearbySqs = rc.senseNearbyMapInfos();
            for (int i = nearbySqs.length; --i >= 0;) {
                MapLocation sqLoc = nearbySqs[i].getMapLocation();
                // filter out impassibles and non boundary blocks(< 13 r^2)
                if (sqLoc.distanceSquaredTo(src) < 13 || nearbySqs[i].isPassable())
                    continue;
                // get min dist to tgt from this impassible
                int currDist = sqLoc.distanceSquaredTo(tgt);
                if (currDist < minDist) {
                    minDist = currDist;
                    minBound = sqLoc;
                }
            }
            // check if this best boundary sq is to left or right of vec to tgt with z of
            // cross, else just random
            if (minBound != null) {
                int zCross = (tgt.x - src.x) * (minBound.y - src.y) - (tgt.y - src.y) * (minBound.x - src.x);
                buhgDir = zCross > 0 ? Rot.LEFT : Rot.RIGHT;
            } else {
                buhgDir = rc.getID() % 2 == 0 ? Rot.LEFT : Rot.RIGHT;
            }
            initBlockDist = src.distanceSquaredTo(tgt);
        }
        turnsBuhgging++;

        // flag for if we end up buhgging around a robot
        // in this case do not update lastbuhgdir, as it may result in us circling
        // around imaginary obstacles schizo
        boolean unitBlock = false;
        // standard buhg movement once a dir decided
        if (buhgDir == Rot.LEFT) {
            // start scanning from ortho to last move if available, else just base off dir
            // to tgt
            Direction moveDir = lastBuhgDir != Direction.CENTER ? lastBuhgDir.rotateRight()
                    : dirTo.rotateLeft();
            for (int i = 7; --i >= 0;) {
                MapLocation currLoc = src.add(moveDir);
                // wall avoidance
                if (!rc.onTheMap(currLoc)) {
                    initBlockDist = src.distanceSquaredTo(tgt);
                    buhgDir = Rot.RIGHT;
                    lastBuhgDir = Direction.CENTER;
                    turnsBuhgging = 0;
                    break;
                }
                if (rc.canMove(moveDir)) {
                    if (!unitBlock)
                        lastBuhgDir = moveDir;
                    // check for buhg exiting conditions(closer to goal than init buhgging dist +
                    // not blocked)
                    if ((src.distanceSquaredTo(tgt) < initBlockDist && directPassable)
                            || turnsBuhgging > 20) {
                        initBlockDist = 9999;
                        buhgDir = Rot.NONE;
                        turnsBuhgging = 0;
                        lastBuhgDir = Direction.CENTER;
                    }
                    return moveDir;
                }
                // if blocked by robot set flag, else clear
                unitBlock = rc.canSenseRobotAtLocation(currLoc);
                moveDir = moveDir.rotateLeft();
            }
        } else {
            Direction moveDir = lastBuhgDir != Direction.CENTER ? lastBuhgDir.rotateLeft()
                    : dirTo.rotateRight();
            for (int i = 7; --i >= 0;) {
                MapLocation currLoc = src.add(moveDir);
                if (!rc.onTheMap(currLoc)) {
                    initBlockDist = src.distanceSquaredTo(tgt);
                    buhgDir = Rot.LEFT;
                    lastBuhgDir = Direction.CENTER;
                    turnsBuhgging = 0;
                    break;
                }
                if (rc.canMove(moveDir)) {
                    if (!unitBlock)
                        lastBuhgDir = moveDir;
                    if ((src.distanceSquaredTo(tgt) < initBlockDist && directPassable)
                            || turnsBuhgging > 20) {
                        initBlockDist = 9999;
                        buhgDir = Rot.NONE;
                        turnsBuhgging = 0;
                        lastBuhgDir = Direction.CENTER;
                    }
                    return moveDir;
                }
                unitBlock = rc.canSenseRobotAtLocation(currLoc);
                moveDir = moveDir.rotateRight();
            }
        }
        return Direction.CENTER;
    }
}
