package v1;

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
        if (src == tgt)
            return Direction.CENTER;
        // some variant buh g with wall avoidance
        Direction dirTo = src.directionTo(tgt);

        // direct greedily(8 dir) if can until impassible 1 tile no los, then buhg
        if (rc.senseMapInfo(src.add(dirTo)).isPassable() && buhgDir == Rot.NONE) {
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

        // standard buhg movement once a dir decided
        if (buhgDir == Rot.LEFT) {
            // start scanning from ortho to last move if available, else just base off dir
            // to tgt
            Direction moveDir = lastBuhgDir != Direction.CENTER ? lastBuhgDir.rotateRight()
                    : dirTo.rotateLeft();
            for (int i = 7; --i >= 0;) {
                // wall avoidance
                if (!rc.onTheMap(src.add(moveDir)))
                    buhgDir = Rot.RIGHT;
                if (rc.canMove(moveDir)) {
                    // check for buhg exiting conditions(closer to goal than init buhgging dist)
                    if (src.distanceSquaredTo(tgt) < initBlockDist || turnsBuhgging > 20) {
                        initBlockDist = 9999;
                        buhgDir = Rot.NONE;
                        turnsBuhgging = 0;
                    }
                    lastBuhgDir = moveDir;
                    return moveDir;
                }
                moveDir = moveDir.rotateLeft();
            }
        } else {
            Direction moveDir = lastBuhgDir != Direction.CENTER ? lastBuhgDir.rotateLeft()
                    : dirTo.rotateRight();
            if (!rc.onTheMap(src.add(moveDir)))
                buhgDir = Rot.LEFT;
            for (int i = 7; --i >= 0;) {
                if (rc.canMove(moveDir)) {
                    if (src.distanceSquaredTo(tgt) < initBlockDist || turnsBuhgging > 20) {
                        initBlockDist = 9999;
                        buhgDir = Rot.NONE;
                        turnsBuhgging = 0;
                    }
                    lastBuhgDir = moveDir;
                    return moveDir;
                }
                moveDir = moveDir.rotateRight();
            }
        }
        return Direction.CENTER;
    }
}
