/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.geometry;

import static com.google.common.geometry.S2RobustCrossProd.robustCrossProd;
import static java.lang.Math.PI;
import static java.lang.Math.asin;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedLongs;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Utility methods for generating test data for S2 unit tests and benchmarks. State consists of a
 * java.util.Random which is used in generating test data.
 */
public class TestDataGenerator {

  /** The default radius for loops. */
  public static final S1Angle DEFAULT_LOOP_RADIUS = kmToAngle(10.0);

  /** Desired distance between nested loops, expressed as a multiple of the edge length. */
  public static final double DEFAULT_NESTED_LOOP_GAP = 5.0;

  /** Desired ratio between the radii of crossing loops. */
  public static final double DEFAULT_CROSSING_LOOP_RADIUS_RATIO = 10.0;

  /** Approximate "effective" radius of the Earth in meters. */
  public static final double EARTH_RADIUS_METERS = 6371010.0;

  /** The default seed for the random number generator. */
  public static final int DEFAULT_RANDOM_SEED = 123455;

  /** The single Random that this TestDataGenerator's random methods are based on. */
  public Random rand;

  /** Constructs a new TestDataGenerator with its internal Random seeded with the default value. */
  public TestDataGenerator() {
    rand = new Random(DEFAULT_RANDOM_SEED);
  }

  /** Constructs a new TestDataGenerator with its internal Random seeded to the provided value. */
  public TestDataGenerator(int seed) {
    rand = new Random(seed);
  }

  /** Resets the random seed to the default value. */
  public void resetSeed() {
    rand.setSeed(DEFAULT_RANDOM_SEED);
  }

  /** Sets the random number generator to the given seed. */
  public void setSeed(int seed) {
    rand.setSeed(seed);
  }

  // Convenience methods that pass through to the underlying Random.

  /** Returns {@link Random#nextDouble()} from the TestDataGenerator's internal Random. */
  public double nextDouble() {
    return rand.nextDouble();
  }

  /** Returns {@link Random#nextInt()} from the TestDataGenerator's internal Random. */
  public int nextInt() {
    return rand.nextInt();
  }

  /** Returns {@link Random#nextInt(int)} from the TestDataGenerator's internal Random. */
  public int nextInt(int n) {
    return rand.nextInt(n);
  }

  /** Returns {@link Random#nextLong()} from the TestDataGenerator's internal Random. */
  public long nextLong() {
    return rand.nextLong();
  }

  /** Returns {@link Random#nextBoolean()} from the TestDataGenerator's internal Random. */
  public boolean nextBoolean() {
    return rand.nextBoolean();
  }

  /** Returns {@link Random#nextBytes(byte[])} from the TestDataGenerator's internal Random. */
  public void nextBytes(byte[] bytes) {
    rand.nextBytes(bytes);
  }

  /** Return a random unit-length vector. */
  public S2Point getRandomPoint() {
    return S2Point.normalize(
        new S2Point(
            2 * rand.nextDouble() - 1, 2 * rand.nextDouble() - 1, 2 * rand.nextDouble() - 1));
  }

  /** Returns a random angle between the given minRadians and maxRadians. */
  public S1Angle randomAngleInRange(double minRadians, double maxRadians) {
    return S1Angle.radians(minRadians + (rand.nextDouble() * (maxRadians - minRadians)));
  }

  /**
   * Return a random cell id at the given level. The distribution is uniform over the space of cell
   * ids, but only approximately uniform over the surface of the sphere.
   */
  public S2CellId getRandomCellId(int level) {
    int face = random(S2CellId.NUM_FACES);
    long pos = rand.nextLong() & ((1L << (2 * S2CellId.MAX_LEVEL)) - 1);
    return S2CellId.fromFacePosLevel(face, pos, level);
  }

  /**
   * Return a random cell id, contained by the given parent id, and at the given level. The given
   * level must be at greater than or equal to the parent level.
   */
  public S2CellId getRandomCellId(S2CellId parent, int level) {
    Preconditions.checkArgument(level >= parent.level());
    int levelDiff = level - parent.level();
    int childrenAtLevel = 1 << (levelDiff * 2);
    return parent.childBegin(level).advance(uniform(childrenAtLevel));
  }

  /**
   * Return a random cell id at a randomly chosen level. The distribution is uniform over the space
   * of cell ids, but only approximately uniform over the surface of the sphere.
   */
  public S2CellId getRandomCellId() {
    return getRandomCellId(random(S2CellId.MAX_LEVEL + 1));
  }

  /** Return a random cell with face, pos, and level all selected uniformly at random. */
  public S2CellId randomCell() {
    int face = rand.nextInt(6);
    long pos = rand.nextLong() & S2CellId.POS_BITS;
    int level = rand.nextInt(30);
    return S2CellId.fromFacePosLevel(face, pos, level);
  }

  /** Return a uniformly distributed double in the range [min, max). */
  public double uniform(double min, double max) {
    Preconditions.checkArgument(min <= max);
    return min + rand.nextDouble() * (max - min);
  }

  /** Return a uniformly distributed integer in the range [0,bound). Requires bound > 0. */
  public int uniform(int bound) {
    Preconditions.checkState(bound > 0);
    return rand.nextInt(bound);
  }

  /** Return a uniformly distributed integer in the range [min, max). */
  public int uniformInt(int min, int max) {
    Preconditions.checkArgument(min <= max);
    return min + rand.nextInt(max - min);
  }

  /**
   * Picks a "base" uniformly from range [0,maxLog] and then return "base" random bits. The effect
   * is to pick a number in the range [0,2^maxLog-1] with bias towards smaller numbers.
   */
  public int skewed(int maxLog) {
    final int base = rand.nextInt(maxLog + 1);

    // This distribution differs slightly from ACMRandom's Skewed, since 0 occurs approximately 3
    // times more than 1 here, and ACMRandom's Skewed never outputs 0.
    return rand.nextInt() & ((1 << base) - 1);
  }

  /** Returns true randomly, approximately one time in {@code n} calls. */
  public boolean oneIn(int n) {
    return rand.nextInt(n) == 0;
  }

  /** Return a right-handed coordinate frame (three orthonormal vectors). */
  public Matrix getRandomFrame() {
    return getRandomFrameAt(getRandomPoint());
  }

  /**
   * Given a unit-length z-axis, compute x- and y-axes such that (x,y,z) is a right-handed
   * coordinate frame (three orthonormal vectors).
   */
  public Matrix getRandomFrameAt(S2Point z) {
    S2Point x = S2Point.normalize(S2Point.crossProd(z, getRandomPoint()));
    S2Point y = S2Point.normalize(S2Point.crossProd(z, x));
    return Matrix.fromCols(x, y, z);
  }

  /** Returns a random number between 0 and n */
  public int random(int n) {
    if (n == 0) {
      return 0;
    }
    return rand.nextInt(n);
  }

  /** Returns a randomly located and sized S2Cap, with area in the range [minArea, maxArea]. */
  public S2Cap getRandomCap(double minArea, double maxArea) {
    double capArea = maxArea * pow(minArea / maxArea, rand.nextDouble());
    Preconditions.checkState(capArea >= minArea && capArea <= maxArea);

    // The surface area of a cap is 2*Pi times its height.
    return S2Cap.fromAxisArea(getRandomPoint(), capArea);
  }

  /** Returns a polygon with given center, number of concentric loops, and vertices per loop. */
  public static S2Polygon concentricLoopsPolygon(
      S2Point center, int numLoops, int numVerticesPerLoop) {
    Matrix m = S2.getFrame(center);
    List<S2Loop> loops = new ArrayList<>(numLoops);
    for (int li = 0; li < numLoops; ++li) {
      List<S2Point> vertices = new ArrayList<>(numVerticesPerLoop);
      double radius = 0.005 * (li + 1) / numLoops;
      double radianStep = 2 * PI / numVerticesPerLoop;
      for (int vi = 0; vi < numVerticesPerLoop; ++vi) {
        double angle = vi * radianStep;
        S2Point p = new S2Point(radius * cos(angle), radius * sin(angle), 1);
        vertices.add(S2.rotate(p, m));
      }
      loops.add(new S2Loop(vertices));
    }
    return new S2Polygon(loops);
  }

  /**
   * Creates "numLoops" nested regular loops around a common center point. All loops have the same
   * random number of vertices (at least "minVertices"). Furthermore, the vertices at the same index
   * position are collinear with the common center point of all the loops. The loop radii decrease
   * exponentially in order to prevent accidental loop crossings when one of the loops is modified.
   */
  protected void addConcentricLoops(
      List<List<S2Point>> outputLoops, int numLoops, int minVertices) {
    Preconditions.checkArgument(numLoops <= 10); // Because radii decrease exponentially.
    S2Point center = getRandomPoint();
    int numVertices = minVertices + uniform(10);
    for (int i = 0; i < numLoops; ++i) {
      S1Angle radius = S1Angle.degrees(80 * Math.pow(0.1, i));
      outputLoops.add(S2Loop.makeRegularVertices(center, radius, numVertices));
    }
  }

  /** Returns 'num' evenly spaced edges all sharing a common center point. */
  public static List<S2Edge> ccwEdgesAbout(S2Point center, int num) {
    List<S2Edge> edges = new ArrayList<>();
    for (int i = 0; i < num; ++i) {
      double angle = 2 * PI / num * i;
      edges.add(new S2Edge(center, latLngToPoint(sin(angle), cos(angle))));
    }
    return edges;
  }

  /** Converts latitude/longitude in radians to an S2Point. */
  public static S2Point latLngToPoint(double lat, double lng) {
    return S2LatLng.fromRadians(lat, lng).toPoint();
  }

  /** Return a point chosen uniformly at random (with respect to area) from the given cap. */
  public S2Point samplePoint(S2Cap cap) {
    // We consider the cap axis to be the "z" axis.  We choose two other axes to complete the
    // coordinate frame.

    S2Point z = cap.axis();
    S2Point x = S2.ortho(z);
    S2Point y = S2Point.crossProd(z, x);

    // The surface area of a spherical cap is directly proportional to its height.  First we choose
    // a random height, and then we choose a random point along the circle at that height.

    double h = rand.nextDouble() * cap.height();
    double theta = 2 * PI * rand.nextDouble();
    double r = sqrt(h * (2 - h)); // Radius of circle.

    // (cos(theta)*r*x + sin(theta)*r*y + (1-h)*z).Normalize()
    return S2Point.normalize(
        S2Point.add(
            S2Point.add(S2Point.mul(x, cos(theta) * r), S2Point.mul(y, sin(theta) * r)),
            S2Point.mul(z, (1 - h))));
  }

  /** Return a random point within the given S2LatLngRect. */
  public S2Point samplePoint(S2LatLngRect rect) {
    // First choose a latitude uniformly with respect to area on the sphere.
    double sinLo = sin(rect.lat().lo());
    double sinHi = sin(rect.lat().hi());
    double lat = asin(sinLo + rand.nextDouble() * (sinHi - sinLo));

    // Now choose longitude uniformly within the given range.
    double lng = rect.lng().lo() + rand.nextDouble() * rect.lng().getLength();

    return S2LatLng.fromRadians(lat, lng).normalized().toPoint();
  }

  /**
   * Returns a random edge uniformly sampled from the given S2ShapeIndex. To avoid repeatedly
   * counting index edges here, the caller must supply numIndexEdges, which can be obtained from
   * S2ShapeUtil.countEdges(index).
   */
  public S2Shape.MutableEdge sampleEdge(S2ShapeIndex index, int numIndexEdges) {
    int e = uniform(numIndexEdges);
    for (S2Shape shape : index.getShapes()) {
      if (shape == null) {
        continue;
      }
      if (e < shape.numEdges()) {
        S2Shape.MutableEdge edge = new S2Shape.MutableEdge();
        shape.getEdge(e, edge);
        return edge;
      }
      e -= shape.numEdges();
    }
    throw new IllegalStateException("Index with no edges?");
  }

  /** Returns a random cell from the given S2ShapeIndex. */
  public S2CellId sampleCell(S2ShapeIndex index) {
    int numCells = 0;
    S2Iterator<S2ShapeIndex.Cell> iter = index.iterator();
    for (; !iter.done(); iter.next()) {
      ++numCells;
    }
    iter.restart();
    for (int i = uniform(numCells); --i >= 0; iter.next()) {
      continue;
    }
    return iter.id();
  }

  /** Returns a random point on the boundary of the given S2Cap. */
  public S2Point sampleBoundary(S2Cap cap) {
    return S2EdgeUtil.getPointOnLine(cap.axis(), getRandomPoint(), cap.radius());
  }

  /**
   * Returns a random point on the boundary of the given R2Rect. Does not sample uniformly by
   * distance; instead will choose one of the four edges with equal probability, and then sample
   * uniformly along the chosen edge.
   */
  public R2Vector sampleBoundary(R2Rect rect) {
    double x;
    double y;
    if (oneIn(2)) {
      // Pick either the bottom or the top edge for y
      y = oneIn(2) ? rect.y().lo() : rect.y().hi();
      // And then sample uniformly in the x dimension along the bottom or top edge.
      x = uniform(rect.x().lo(), rect.x().hi());
    } else {
      // Pick either the left or right edge for x
      x = oneIn(2) ? rect.x().lo() : rect.x().hi();
      // and then sample uniformly in the y dimension for the left or right edge.
      y = uniform(rect.y().lo(), rect.y().hi());
    }
    return new R2Vector(x, y);
  }

  /** Ensures the index is built for the given loop. */
  @CanIgnoreReturnValue
  @SuppressWarnings("VisibleForTestingUsed")
  public static S2Loop indexLoop(S2Loop loop) {
    boolean unused = loop.contains(S2Cell.fromFace(0));
    Preconditions.checkState(loop.index.isFresh());
    return loop;
  }

  /**
   * Make a fractal loop with the given size and approximate number of vertices. The same fractal is
   * returned for each call, given the same parameters.
   *
   * <p>See also {@link #makeRandomFractal(S2LatLng, S2Angle, int)} which takes a center point, and
   * returns random fractals.
   */
  public static S2Loop makeFractal(int radiusDegrees, int numVertices) {
    S2FractalBuilder fractalBuilder = new S2FractalBuilder(new Random(1));
    fractalBuilder.setLevelForApproxMaxEdges(numVertices);
    return fractalBuilder.makeLoop(S2.getFrame(S2Point.X_POS), S1Angle.degrees(radiusDegrees));
  }

  /**
   * Generates a random fractal polygon with the given center, radius, and number of edges. Uses
   * TestDataGenerator's "rand" to produce random fractals for each call.
   */
  public S2Polygon makeRandomFractal(S2LatLng center, S1Angle radius, int edges) {
    S2FractalBuilder fractalBuilder = new S2FractalBuilder(rand);
    fractalBuilder.setLevelForApproxMaxEdges(edges);
    return new S2Polygon(fractalBuilder.makeLoop(upFrameAt(center), radius));
  }

  /** Returns a frame in the "up" (positive z) direction at a given point. */
  public static Matrix upFrameAt(S2LatLng center) {
    S2Point z = center.toPoint();
    S2Point x = robustCrossProd(z, S2Point.Z_POS).normalize();
    S2Point y = robustCrossProd(z, x).normalize();
    return Matrix.fromCols(x, y, z);
  }

  /** Convert a distance on the Earth's surface to an angle. */
  public static S1Angle kmToAngle(double km) {
    return metersToAngle(1000 * km);
  }

  /** Convert a distance on the Earth's surface to an angle. */
  public static S1Angle metersToAngle(double meters) {
    return S1Angle.radians(meters / EARTH_RADIUS_METERS);
  }

  /**
   * Returns a pair of nested loops, such that the second loop in the returned list is nested within
   * the first. Both loops are centered at 'p', and each has 'numVertices' vertices. The outer loop
   * has the given radius 'outerRadius'. The inner loop is inset by a small distance ('gap') from
   * the outer loop which is approximately equal to 'gapEdgeMultiple' times the edge length of the
   * outer loop. (This allows better spatial indexing, which becomes less effective at pruning
   * intersection candidates as the loops get closer together.)
   *
   * <p>Caveats: The gap is actually measured to the incircle of the outer loop, and the gap is
   * clamped if necessary to prevent the inner loop from becoming vanishingly small. (Rule of thumb:
   * to obtain a 'gapEdgeMultiple' of 'm', the loops must have approximately 7 * m vertices or more.
   */
  public static List<S2Loop> makeNestedLoopPair(
      S1Angle outerRadius, double gapEdgeMultiple, int numVertices, S2Point p) {
    // The inner loop is inscribed within the incircle (maximum inscribed circle) of the outer
    // loop.
    S1Angle incircleRadius = S1Angle.radians(outerRadius.radians() * cos(PI / numVertices));
    S1Angle edgeLen = S1Angle.radians(outerRadius.radians() * (2 * PI / numVertices));

    // If the edge count is too small, it may not be possible to inset the inner loop by the given
    // multiple of the edge length.  We handle this by clamping 'innerRadius' to be at least 1% of
    // 'outerRadius'.
    S1Angle innerRadius =
        S1Angle.radians(
            max(
                incircleRadius.radians() - gapEdgeMultiple * edgeLen.radians(),
                0.01 * incircleRadius.radians()));

    // Generate two loops with the same center.
    return Arrays.asList(
        S2Loop.makeRegularLoop(p, outerRadius, numVertices),
        S2Loop.makeRegularLoop(p, innerRadius, numVertices));
  }

  /**
   * Returns a random pair of crossing loops. The first loop in the returned list will have center
   * 'aPoint' and radius 'aRadius'. The second will have its center along the arc containing aPoint
   * and bPoint, and it will have radius 'bRadius'. Both loops have 'numVertices' vertices.
   */
  public List<S2Loop> makeCrossingLoopPair(
      S1Angle aRadius, S1Angle bRadius, int numVertices, S2Point aPoint, S2Point bPoint) {
    // The edges of each loop are bounded by two circles, one circumscribed around the loop (the
    // circumcircle), and the other inscribed within the loop (the incircle).  Our strategy is to
    // place the smaller loop such that its incircle crosses both circles of the larger loop.
    double maxRadius = max(aRadius.radians(), bRadius.radians());
    double minRadius = min(aRadius.radians(), bRadius.radians());

    // Check that the smaller loop is big enough that its incircle can span the gap between the
    // incircle and the circumcircle of the larger loop.  The incircle factor is the loop radius
    // divided by its incircle radius.
    double incircleFactor = cos(PI / numVertices);
    Preconditions.checkState(minRadius * incircleFactor > maxRadius * (1 - incircleFactor));

    // Compute the range of distances between the two loop centers such that the incircle of the
    // smaller loop crosses both circles of the larger loop.
    double minDist = maxRadius - incircleFactor * minRadius;
    double maxDist = incircleFactor * (minRadius + maxRadius);

    // Now generate a pair of loops whose centers are separated by distances in the given range.
    // Loop orientations are chosen randomly.
    S1Angle angle = S1Angle.radians(minDist + rand.nextDouble() * (maxDist - minDist));
    S2Point bCenter = S2EdgeUtil.getPointOnLine(aPoint, bPoint, angle);
    return Arrays.asList(
        S2Loop.makeRegularLoop(aPoint, aRadius, numVertices),
        S2Loop.makeRegularLoop(bCenter, bRadius, numVertices));
  }

  /**
   * Returns the pair of crossing loops given by makeCrossingLoopPair() when using public values for
   * the radii of the two loops. The loops will have centers 'aPoint' and 'bPoint', and will have
   * 'numVertices' vertices each.
   */
  public List<S2Loop> makeCrossingLoopPairDefault(int numVertices, S2Point aPoint, S2Point bPoint) {
    S1Angle aRadius = DEFAULT_LOOP_RADIUS;
    S1Angle bRadius = S1Angle.radians(aRadius.radians() * DEFAULT_CROSSING_LOOP_RADIUS_RATIO);
    return makeCrossingLoopPair(aRadius, bRadius, numVertices, aPoint, bPoint);
  }

  /**
   * Returns a pair of disjoint loops. The loops are constructed so that it is impossible to
   * determine the relationship between them based solely on their bounds (they could be nested,
   * crossing, or disjoint). The outer loop (1st loop in returned list) will look somewhat like the
   * outline of the letter "C": it consists of two nested loops (the "outside shell" and the "inside
   * shell"), which each have a single edge removed and are then joined together to form a single
   * loop. The inner loop (2nd loop in returned list) is then nested within the inside shell of the
   * outer loop.
   *
   * <p>The outer loop has 'numVertices' vertices split between its outside and inside shells. The
   * radius of the outside shell is 'outerRadius', while the radius of the inside shell is (0.9 *
   * outerRadius).
   *
   * <p>The inner loop has 'numVertices' vertices, and is separated from the inside shell of the
   * outer loop by a small distance ("gap") which is approximately equal to 'gapMultipleEdges' times
   * the edge length of the inside shell. (See getNestedLoopPair for details.)
   */
  public static List<S2Loop> makeDisjointLoopPair(
      S1Angle outerRadius, double gapEdgeMultiple, int numVertices, S2Point center) {
    // Compute the radius of the inside shell of the outer loop, the edge length of the outer
    // shell, and finally the incircle radius of the inside shell (this is the maximum possible
    // radius of the inner loop).
    S1Angle outerInsideRadius =
        S1Angle.radians(0.9 * outerRadius.radians() * cos(2 * PI / numVertices));
    S1Angle edgeLen = S1Angle.radians(outerInsideRadius.radians() * (PI / numVertices));
    S1Angle incircleRadius =
        S1Angle.radians(outerInsideRadius.radians() * cos(2 * PI / numVertices));

    // See comments in getNestedLoopPair().
    S1Angle innerRadius =
        S1Angle.radians(
            max(
                incircleRadius.radians() - gapEdgeMultiple * edgeLen.radians(),
                0.01 * incircleRadius.radians()));

    S2Loop outerOutside = S2Loop.makeRegularLoop(center, outerRadius, max(4, numVertices / 2));
    S2Loop outerInside = S2Loop.makeRegularLoop(center, outerInsideRadius, max(4, numVertices / 2));
    List<S2Point> vertices =
        Lists.newArrayListWithCapacity(outerInside.numVertices() + outerOutside.numVertices());

    // Join together the outside and inside shells to form the outer loop.
    for (int j = outerInside.numVertices() - 1; j >= 0; j--) {
      vertices.add(outerInside.vertex(j));
    }
    for (int j = 0; j < outerOutside.numVertices(); ++j) {
      vertices.add(outerOutside.vertex(j));
    }

    return Arrays.asList(
        new S2Loop(vertices), S2Loop.makeRegularLoop(center, innerRadius, numVertices));
  }

  /**
   * Returns {@code numIds} S2 cell IDs at level {@link S2CellId#MAX_LEVEL}, evenly spread over the
   * range of valid cells at that level.
   */
  public static S2CellId[] generateEvenlySpacedIds(int numIds) {
    S2CellId[] ids = new S2CellId[numIds];
    long start = S2CellId.begin(S2CellId.MAX_LEVEL).id();
    long end = S2CellId.end(S2CellId.MAX_LEVEL).id();
    long delta = UnsignedLongs.divide(end - start, numIds);
    for (int i = 0; i < numIds; i++) {
      ids[i] = new S2CellId(start + i * delta);
    }
    return ids;
  }

  /**
   * Adds the given points to the given S2PointIndex, with their index in the list as the associated
   * data.
   */
  public static void addPoints(S2PointIndex<Integer> index, List<S2Point> points) {
    for (int i = 0; i < points.size(); i++) {
      index.add(points.get(i), i);
    }
  }

  /** A collection of techniques for generating points in a cap, for testing or benchmarking. */
  public enum PointFactory {
    /**
     * Generator for points regularly spaced along a circle. The circle is centered within the query
     * cap and occupies 25% of its area, so that random query points have a 25% chance of being
     * inside the circle.
     *
     * <p>Points along a circle are nearly the worst case for distance calculations, since many
     * points are nearly equidistant from any query point that is not immediately adjacent to the
     * circle.
     */
    CIRCLE {
      @Override
      public List<S2Point> createPoints(TestDataGenerator data, S2Cap queryCap, int numPoints) {
        return S2Loop.makeRegularVertices(
            queryCap.axis(), S1Angle.radians(0.5 * queryCap.angle().radians()), numPoints);
      }
    },

    /** Generator for points of a fractal whose convex hull approximately matches the query cap. */
    FRACTAL {
      @Override
      public List<S2Point> createPoints(TestDataGenerator data, S2Cap queryCap, int numPoints) {
        S2FractalBuilder builder = new S2FractalBuilder(data.rand);
        builder.setLevelForApproxMaxEdges(numPoints);
        builder.setFractalDimension(1.5);
        return builder.makeVertices(data.getRandomFrameAt(queryCap.axis()), queryCap.angle());
      }
    },

    /** Generator for points on a square grid that includes the entire query cap. */
    GRID {
      @Override
      public List<S2Point> createPoints(TestDataGenerator data, S2Cap queryCap, int numPoints) {
        int sqrtNumPoints = (int) ceil(sqrt(numPoints));
        Matrix frame = data.getRandomFrameAt(queryCap.axis());
        double radius = queryCap.angle().radians();
        double spacing = 2 * radius / sqrtNumPoints;
        List<S2Point> points = Lists.newArrayList();
        for (int i = 0; i < sqrtNumPoints; ++i) {
          for (int j = 0; j < sqrtNumPoints; ++j) {
            S2Point q =
                new S2Point(
                        tan((i + 0.5) * spacing - radius), tan((j + 0.5) * spacing - radius), 1.0)
                    .normalize();
            points.add(S2.fromFrame(frame, q));
          }
        }
        return points;
      }
    };

    /**
     * Returns a list of approximately {@code numPoints} random points sampled from {@code queryCap}
     * by some geometric strategy. Typically the indexed points will occupy some fraction of this
     * cap.)
     */
    public abstract List<S2Point> createPoints(
        TestDataGenerator data, S2Cap queryCap, int numPoints);
  }

  /** Builds the internal index for the given S2Polygon. */
  public static S2Polygon index(S2Polygon polygon) {
    polygon.index().applyUpdates();
    return polygon;
  }

  /** Returns a single loop polygon that contains the given polygon. */
  public static S2Polygon getBoundingPolygon(S2Polygon polygon) {
    S2Cap cap = polygon.getCapBound();
    List<S2Loop> loops = new ArrayList<>(1);
    int numCapEdges = 8;
    loops.add(
        S2Loop.makeRegularLoop(
            cap.axis(),
            S1Angle.radians(cap.angle().radians() / cos(PI / numCapEdges)),
            numCapEdges));
    return new S2Polygon(loops);
  }

  /**
   * Polygon factories for testing or benchmarking. Each implements newPolygon(int numLoops, int
   * totalNumVertices) which returns a polygon of a particular type, with the specified number of
   * loops and total number of vertices. The number of vertices per loop will be at least three,
   * which may cause the total number of vertices in the polygon to be larger than what was
   * specified.
   */
  public enum PolygonFactory {
    CONCENTRIC_LOOPS {
      @Override
      public S2Polygon newPolygon(TestDataGenerator data, int numLoops, int totalNumVertices) {
        int numVerticesPerLoop = max(3, totalNumVertices / numLoops);
        return concentricLoopsPolygon(data.getRandomPoint(), numLoops, numVerticesPerLoop);
      }
    },

    /**
     * Constructs polygons consisting of nested regular loops. Note that 'numLoops' should be fairly
     * small (less than 20), otherwise the edges can get so small that loops become invalid. (This
     * is checked.)
     */
    NESTED_LOOPS {
      @Override
      public S2Polygon newPolygon(TestDataGenerator data, int numLoops, int totalNumVertices) {
        int numVerticesPerLoop = max(3, totalNumVertices / numLoops);
        List<S2Loop> loops = new ArrayList<>();
        // Each loop is smaller than the previous one by a fixed ratio. The first factor below
        // compensates for loop edges cutting across the interior of the circle, while the second
        // accounts for the separation requested by the user (which is measured relative to the
        // radius of the inner loop.
        double scale = cos(PI / numVerticesPerLoop) / (1 + LOOP_SEPARATION_FRACTION);
        S2Point center = data.getRandomPoint();
        S1Angle radius = DEFAULT_LOOP_RADIUS;
        for (int i = 0; i < numLoops; ++i) {
          S2Loop loop = S2Loop.makeRegularLoop(center, radius, numVerticesPerLoop);
          checkEdgeLength(loop);
          loops.add(loop);
          radius = S1Angle.radians(radius.radians() * scale);
        }
        return new S2Polygon(loops);
      }
    },

    /**
     * Constructs polygons consisting of nested fractal loops. Note that 'numLoops' should be fairly
     * small (less than 20), otherwise the edges can get so small that loops become invalid. (This
     * is checked.)
     */
    NESTED_FRACTALS {
      @Override
      public S2Polygon newPolygon(TestDataGenerator data, int numLoops, int totalNumVertices) {
        int numVerticesPerLoop = max(3, totalNumVertices / numLoops);
        List<S2Loop> loops = new ArrayList<>();
        S2FractalBuilder fractal = new S2FractalBuilder(data.rand);
        fractal.setFractalDimension(LOOP_FRACTAL_DIMENSION);
        fractal.setLevelForApproxMaxEdges(numVerticesPerLoop);

        // Scale each loop so that it fits entirely within the previous loop, with a gap equal to
        // some fraction of the inner loop's radius.
        double scale =
            (fractal.minRadiusFactor()
                / fractal.maxRadiusFactor()
                / (1 + LOOP_SEPARATION_FRACTION));
        S2Point center = data.getRandomPoint();
        S1Angle radius = DEFAULT_LOOP_RADIUS;
        for (int i = 0; i < numLoops; ++i) {
          S2Loop loop = fractal.makeLoop(S2.getFrame(center), radius);
          checkEdgeLength(loop);
          loops.add(loop);
          radius = S1Angle.radians(radius.radians() * scale);
        }
        return new S2Polygon(loops);
      }
    },

    /** Constructs polygons consisting of a grid of regular loops. */
    LOOP_GRID {
      @Override
      public S2Polygon newPolygon(TestDataGenerator data, int numLoops, int totalNumVertices) {
        int numVerticesPerLoop = max(3, totalNumVertices / numLoops);
        // Reduce the loop radius if necessary to limit distortion caused by the spherical
        // projection
        // (the total diameter of the grid is clamped to one-quarter of the sphere). We then
        // reduce the loop radius by the maximum amount of distortion at the edges of the grid to
        // ensure that the loops do not intersect.
        int sqrtNumLoops = (int) ceil(sqrt(numLoops));
        double spacingMultiplier = 1 + LOOP_SEPARATION_FRACTION;
        double maxAngle =
            min(PI / 4, sqrtNumLoops * spacingMultiplier * DEFAULT_LOOP_RADIUS.radians());
        double spacing = 2 * maxAngle / sqrtNumLoops;
        double radius = 0.5 * spacing * cos(maxAngle) / spacingMultiplier;

        // If 'numLoops' is not a perfect square, make the grid slightly larger and leave some
        // locations empty.
        int left = numLoops;
        List<S2Loop> loops = new ArrayList<>();
        for (int i = 0; i < sqrtNumLoops && left > 0; ++i) {
          for (int j = 0; j < sqrtNumLoops && left > 0; ++j, --left) {
            S2Point center =
                new S2Point(
                    tan((i + 0.5) * spacing - maxAngle), tan((j + 0.5) * spacing - maxAngle), 1.0);
            S2Loop loop =
                S2Loop.makeRegularLoop(center, S1Angle.radians(radius), numVerticesPerLoop);
            checkEdgeLength(loop);
            loops.add(loop);
          }
        }

        return new S2Polygon(loops);
      }
    };

    /** Spacing between adjacent loops (e.g., in a grid), as a fraction of the loop radius. */
    static final double LOOP_SEPARATION_FRACTION = 0.1;

    /** Default dimension for fractal loops. */
    static final double LOOP_FRACTAL_DIMENSION = 1.2;

    /** The factory method. */
    public abstract S2Polygon newPolygon(
        TestDataGenerator data, int numLoops, int totalNumVertices);
  }

  /**
   * Since our indexing is based on S2CellIds, it becomes inefficient when loop edges are much
   * smaller than the leaf cell size (which is about 1cm). Since edges smaller than 1mm are not
   * typically needed for geographic data, we have a sanity check so that we don't accidentally
   * generate such data in the benchmarks.
   */
  protected static void checkEdgeLength(S2Loop loop) {
    S1Angle minEdgeLength = metersToAngle(0.001);
    for (int j = 0; j < loop.numVertices(); ++j) {
      Preconditions.checkState(
          new S1Angle(loop.vertex(j), loop.vertex(j + 1)).greaterThan(minEdgeLength));
    }
  }

  /**
   * An interface for factories that return a single, serializable S2Shape in an S2ShapeIndex for
   * benchmarking or testing. Randomness, if used, is obtained from the enclosing
   * TestDataGenerator's Random.
   */
  public interface ShapeFactory {
    // TODO(user): Move all relevant test code to ShapeFactory, away from directly
    // using makeRegularLoop(), S2FractalBuilder(), etc.

    /**
     * Returns a shape that approximately fills the given cap, if the factory produces shapes of
     * variable size, and with approximately the specified number of edges, if the factory produces
     * shapes with a variable number of edges.
     */
    S2ShapeIndex getShape(S2Cap indexCap, int numEdges);
  }

  /**
   * Generates a single point at the center of the given shapeCap. Has no internal state, ignores
   * shapeCap.radius and numEdges.
   */
  @SuppressWarnings("ClassCanBeStatic") // for consistency with other ShapeFactory implementations.
  public class PointShapeFactory implements ShapeFactory {
    @Override
    public S2ShapeIndex getShape(S2Cap shapeCap, int numEdges) {
      return S2ShapeIndex.fromShapes(S2Point.Shape.singleton(shapeCap.axis()));
    }
  }

  /**
   * Generates a regular loop that approximately fills the given shapeCap. Regular loops are nearly
   * the worst case for distance calculations, since many edges are nearly equidistant from any
   * query point that is not immediately adjacent to the loop.
   *
   * <p>Not pseudo-random: Every Regular Loop for a given shapeCap and numEdges is the same.
   */
  @SuppressWarnings("ClassCanBeStatic") // for consistency with other ShapeFactory implementations.
  public class RegularLoopShapeFactory implements ShapeFactory {
    @Override
    public S2ShapeIndex getShape(S2Cap shapeCap, int numEdges) {
      return S2ShapeIndex.fromShapes(
          S2LaxPolygonShape.fromLoop(
              S2Loop.makeRegularLoop(shapeCap.axis(), shapeCap.angle(), numEdges).vertices()));
    }
  }

  /** Generates a pseudo-random fractal loop that approximately fills the given shapeCap. */
  public class FractalLoopShapeFactory implements ShapeFactory {
    private final S2FractalBuilder fractalBuilder = new S2FractalBuilder(rand);

    @Override
    public S2ShapeIndex getShape(S2Cap shapeCap, int numEdges) {
      fractalBuilder.setLevelForApproxMaxEdges(numEdges);
      return S2ShapeIndex.fromShapes(
          S2LaxPolygonShape.fromLoop(
              fractalBuilder
                  .makeLoop(getRandomFrameAt(shapeCap.axis()), shapeCap.angle())
                  .vertices()));
    }
  }

  /** Generates a pseudo-random cloud of 'numPoints' points sampled from the given S2Cap. */
  public class PointCloudShapeFactory implements ShapeFactory {
    @Override
    public S2ShapeIndex getShape(S2Cap shapeCap, int numPoints) {
      ArrayList<S2Point> points = new ArrayList<>();
      for (int i = 0; i < numPoints; ++i) {
        points.add(samplePoint(shapeCap));
      }
      return S2ShapeIndex.fromShapes(S2Point.Shape.fromList(points));
    }
  }

  /** An enumeration of available shape factories, with a method to instantiate them. */
  public enum ShapeFactoryEnum {
    SINGLE_POINT {
      @Override
      public ShapeFactory getFactory(TestDataGenerator generator) {
        return generator.new PointShapeFactory();
      }
    },
    REGULAR_LOOP {
      @Override
      public ShapeFactory getFactory(TestDataGenerator generator) {
        return generator.new RegularLoopShapeFactory();
      }
    },
    FRACTAL_LOOP {
      @Override
      public ShapeFactory getFactory(TestDataGenerator generator) {
        return generator.new FractalLoopShapeFactory();
      }
    },
    POINT_CLOUD {
      @Override
      public ShapeFactory getFactory(TestDataGenerator generator) {
        return generator.new PointCloudShapeFactory();
      }
    };

    public abstract ShapeFactory getFactory(TestDataGenerator generator);

    /**
     * Returns the enum value corresponding to the given ordinal. This should be built into Java
     * enums, but unfortunately is not.
     */
    public static ShapeFactoryEnum valueOf(int ordinal) {
      if (ordinal == SINGLE_POINT.ordinal()) {
        return SINGLE_POINT;
      } else if (ordinal == REGULAR_LOOP.ordinal()) {
        return REGULAR_LOOP;
      } else if (ordinal == FRACTAL_LOOP.ordinal()) {
        return FRACTAL_LOOP;
      } else if (ordinal == POINT_CLOUD.ordinal()) {
        return POINT_CLOUD;
      }
      throw new IllegalArgumentException("Unknown ShapeFactoryEnum ordinal: " + ordinal);
    }
  }
}
