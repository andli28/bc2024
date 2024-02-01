package flagbot;

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

    // normalizes and calculates z of cross product of 2 2d vecs
    public static double normZCross(int x1, int y1, int x2, int y2) {
        double v1norm = Math.sqrt(x1 * x1 + y1 * y1);
        double v2norm = Math.sqrt(x2 * x2 + y2 * y2);
        double x1f = x1 / v1norm;
        double y1f = y1 / v1norm;
        double x2f = x2 / v2norm;
        double y2f = y2 / v2norm;
        return x1f * y2f - x2f * y1f;
    }

    // does not explicitly check movement cd but does canmove checks
    public static Direction pathfind(MapLocation src, MapLocation tgt) throws GameActionException {
        assert tgt != null;

        if (!rc.isMovementReady() || src.equals(tgt))
            return Direction.CENTER;

        // use bfs if we have > 5k bytecode, otherwise do buhg if no bytecode or no
        // result from bfs
        if (Clock.getBytecodesLeft() > 5000) {
            Direction bfsDir = Bfs.getBestDir(tgt);
            // make sure bfs returns dir that is not null and gets u closer to your target
            if (bfsDir != null) {
                return bfsDir;
            }
        }

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
                int currDist = travelDistance(curr, tgt);
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
            int minDistL = 9999;
            MapLocation minBoundL = null;
            int minDistR = 9999;
            MapLocation minBoundR = null;

            // scan nearby impassibles, only looking at ones that intersect with max vision
            // ring
            MapInfo[] nearbySqs = rc.senseNearbyMapInfos();
            for (int i = nearbySqs.length; --i >= 0;) {
                MapLocation sqLoc = nearbySqs[i].getMapLocation();
                // filter out impassibles and non boundary blocks(< 13 r^2)
                if (sqLoc.distanceSquaredTo(src) < 13 || nearbySqs[i].isPassable())
                    continue;
                // get min dist to tgt from this impassible
                int currDist = travelDistance(sqLoc, tgt);
                // note the closest impass to tgt on both left and right
                double zCross = normZCross(tgt.x - src.x, tgt.y - src.y, sqLoc.x - src.x, sqLoc.y - src.y);
                if (zCross > 0 && currDist < minDistL) {
                    minDistL = currDist;
                    minBoundL = sqLoc;
                } else if (zCross < 0 && currDist < minDistR) {
                    minDistR = currDist;
                    minBoundR = sqLoc;
                }
            }
            // if impass exists on both sides, choose dir with impass closest to tgt
            if (minBoundL != null && minBoundR != null) {
                buhgDir = minDistL < minDistR ? Rot.LEFT : Rot.RIGHT;
                // if no impass on both sides random
            } else if (minBoundL == null && minBoundR == null) {
                buhgDir = rc.getRoundNum() % 2 == 0 ? Rot.LEFT : Rot.RIGHT;
                // if impass on only 1 side choose more open side
            } else {
                buhgDir = minBoundL == null ? Rot.LEFT : Rot.RIGHT;
            }
            initBlockDist = travelDistance(src, tgt);
        }
        turnsBuhgging++;

        // flag for if we end up buhgging around a robot
        // in this case do not update lastbuhgdir, as it may result in us circling
        // around imaginary obstacles schizo
        boolean unitBlock = false;
        // standard buhg movement once a dir decided
        Direction moveDir = buhgDir == Rot.LEFT ? (lastBuhgDir != Direction.CENTER ? lastBuhgDir.rotateRight()
                : dirTo.rotateLeft())
                : (lastBuhgDir != Direction.CENTER ? lastBuhgDir.rotateLeft()
                        : dirTo.rotateRight());
        // -45, -90, then <=-45
        for (int i = 8; --i >= 0;) {
            MapLocation currLoc = src.add(moveDir);
            // wall avoidance
            if (!rc.onTheMap(currLoc)) {
                initBlockDist = travelDistance(src, tgt);
                buhgDir = buhgDir == Rot.LEFT ? Rot.RIGHT : Rot.LEFT;
                lastBuhgDir = Direction.CENTER;
                turnsBuhgging = 0;
                break;
            }
            if (rc.canMove(moveDir)) {
                if (!unitBlock)
                    lastBuhgDir = moveDir;
                // check for buhg exiting conditions(closer to goal than init buhgging dist +
                // not blocked)
                if ((travelDistance(src, tgt) < initBlockDist && directPassable)
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

            // -45 then we go -90 first then continue sweeping away from obstacle buh buh
            // buh
            if (i == 7) {
                moveDir = buhgDir == Rot.LEFT ? moveDir.rotateRight() : moveDir.rotateLeft();
            } else {
                moveDir = buhgDir == Rot.LEFT ? moveDir.rotateLeft() : moveDir.rotateRight();
            }
        }
        return Direction.CENTER;
    }

    public static Direction pathfindHome() throws GameActionException {
        rc.setIndicatorString("returning: " + Info.closestSpawn.toString());
        return Pathfinder.pathfind(rc.getLocation(), Info.closestSpawn);
    }

    public static int bfsDist(MapLocation src, MapLocation tgt) throws GameActionException {
        // TODO: implement bfs to find the travel distance between src and tgt
        // returns -1 if we can't reach tgt based on our sensing radius
        return -1;
    }

    // Gives the travelDistance between 2 points on the map. Not accurate, but
    // useful for ranking
    public static int travelDistance(MapLocation src, MapLocation tgt) throws GameActionException {
        // distance between src and tgt is max(dx, dy)
        int dx = Math.abs(src.x - tgt.x);
        int dy = Math.abs(src.y - tgt.y);
        int travelDist = 10 * Math.max(dx, dy); // given greater weight

        // also take into account the distance between the 2 points on the map
        int taxicabDistance = Math.abs(src.x - tgt.x) + Math.abs(src.y - tgt.y);

        return travelDist + taxicabDistance;
    }

    // Heuristic for travel distance
    public static int trueTravelDistance(MapLocation src, MapLocation tgt) throws GameActionException {
        // distance between src and tgt is max(dx, dy)
        int dx = Math.abs(src.x - tgt.x);
        int dy = Math.abs(src.y - tgt.y);
        int travelDist = Math.max(dx, dy);

        return travelDist;
    }

    public static int trueTravelDistance(Direction dir, MapLocation tgt) throws GameActionException {
        return trueTravelDistance(rc.getLocation().add(dir), tgt);
    }

    /**
     * passableDirectionTowards returns the direction that is passable towards the
     * target,
     * prefering the given direction, then left, then right.
     * returns Direction.CENTER if no passable direction exists
     *
     * @param dir the direction to prefer
     * @return the direction that is passable towards the target
     */
    public static Direction passableDirectionTowards(Direction dir) throws GameActionException {
        // return dir if its passable
        if (rc.senseMapInfo(rc.getLocation().add(dir)).isPassable())
            return dir;
        // else return left or right if possible
        Direction left = dir.rotateLeft();
        Direction right = dir.rotateRight();
        if (rc.senseMapInfo(rc.getLocation().add(left)).isPassable())
            return left;
        if (rc.senseMapInfo(rc.getLocation().add(right)).isPassable())
            return right;
        return Direction.CENTER;
    }

}
