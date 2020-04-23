package uob.flexiweld;

import org.opencv.core.Point;

/**
 * A {@code Line} object represents a straight line in 2D space. A line has a start point ({@link Line#getStart()}) and
 * an endpoint ({@link Line#getEnd()}), which are both stored as {@link Point} objects. Unlike {@code Point} objects,
 * {@code Line} objects are immutable and cannot be changed once created.
 * <p></p>
 * The class {@code Line} also contains some useful static methods for comparing and manipulating lines.
 * @author Finin Quincey
 */
public class Line {

	private final Point start;
	private final Point end;

	/**
	 * Creates a new {@code Line} with the given start and end points.
	 * @param start The start point of the line.
	 * @param end The end point of the line.
	 */
	public Line(Point start, Point end){
		this.start = start;
		this.end = end;
	}

	/**
	 * Creates a new {@code Line} with the given start and end points.
	 * @param x1 The x coordinate of the start point of the line.
	 * @param y1 The y coordinate of the start point of the line.
	 * @param x2 The x coordinate of the end point of the line.
	 * @param y2 The y coordinate of the end point of the line.
	 */
	public Line(double x1, double y1, double x2, double y2){
		this(new Point(x1, y1), new Point(x2, y2));
	}

	// Clone them because Line should be immutable, and Point isn't for some reason

	/** Returns a copy of the start {@link Point} of this line. */
	public Point getStart(){
		return start.clone();
	}

	/** Returns a copy of the end {@link Point} of this line. */
	public Point getEnd(){
		return end.clone();
	}

	/** Returns the point halfway between this line's start and end points. */
	public Point midpoint(){
		return new Point((start.x + end.x)/2, (start.y + end.y)/2);
	}

	/** Returns the length of this line. */
	public double length(){
		return Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
	}

	/** Returns the angle between this line and the positive x axis, where anticlockwise rotation is positive. */
	public double angle(){
		return Math.atan2(end.y - start.y, end.x - start.x);
	}

	/** Returns a {@link Point} object representing the vector from the start to the end of this line. */
	public Point direction(){
		return new Point(end.x - start.x, end.y - start.y);
	}

	/** Returns the gradient of this line. For vertical or almost-vertical lines, the returned gradient is infinite. */
	public double gradient(){
		double dx = (end.x - start.x);
		double dy = (end.y - start.y);
		return dy/dx;
	}

	/** Returns the perpendicular distance from this line to the given point. */
	public double distanceTo(Point point){
		Line hypot = new Line(this.start, point);
		return hypot.length() * Math.sin(acuteAngleBetween(this, hypot));
	}

	/** Returns the point on this line that is nearest to the given point. The returned point may be beyond either end
	 * of the line. */
	public Point nearestPointTo(Point point){
		Line hypot = new Line(this.start, point);
		// Yes this should be angleBetween and not acuteAngleBetween, this time direction matters!
		double fractionAlongLine = (hypot.length() * Math.cos(angleBetween(this, hypot))) / length();
		return new Point(start.x + (end.x - start.x) * fractionAlongLine, start.y + (end.y - start.y) * fractionAlongLine);
	}

	// Unary operations

	// Use past-tense names to emphasise that these are copies, not modifying the existing object

	/** Returns a copy of this line with the start and end points swapped. */
	public Line reversed(){
		return new Line(end, start);
	}

	/** Returns a copy of this line, with the start and end point swapped if necessary so that the start x coordinate
	 * of the returned line is less than its end x coordinate. In other words, constrains the angle to between -pi/2
	 * and pi/2, whilst keeping the length the same. */
	public Line xRectified(){
		Point start = this.start.x > this.end.x ? this.end : this.start;
		Point end = this.start.x > this.end.x ? this.start : this.end;
		return new Line(start, end);
	}

	/** Returns a copy of this line, with the start and end point swapped if necessary so that the start y coordinate
	 * of the returned line is less than its end y coordinate. In other words, constrains the angle to between 0
	 * and pi, whilst keeping the length the same. */
	public Line yRectified(){
		Point start = this.start.y > this.end.y ? this.end : this.start;
		Point end = this.start.y > this.end.y ? this.start : this.end;
		return new Line(start, end);
	}

	/**
	 * Returns a copy of this line, extended equally in both directions such that the overall length is increased by
	 * the given fraction.
	 * @param fraction The fraction by which to extend the length of the line. Negative values will shorten the line.
	 * @return The resulting extended line. The length of this line will be equal to the length of the original line
	 * multiplied by {@code (1 + fraction)}. If this results in a negative length, the returned line
	 * will be in the opposite direction to the original ({@code line.extended(-2)} is therefore equivalent to
	 * {@code line.reversed()}).
	 */
	public Line extended(float fraction){
		return this.extended(fraction/2, fraction/2);
	}

	/**
	 * Returns a copy of this line, extended by the given fraction at each end.
	 * @param startFraction The fraction by which to extend the length of the line beyond its start point. Negative
	 *                      values will shorten the line.
	 * @param endFraction The fraction by which to extend the length of the line beyond its end point. Negative
	 *                    values will shorten the line.
	 * @return The resulting extended line. The length of this line will be equal to the length of the original line
	 * multiplied by {@code (1 + startFraction + endFraction)}. If this results in a negative length, the returned line
	 * will be in the opposite direction to the original ({@code line.extended(-1, -1)} is therefore equivalent to
	 * {@code line.reversed()}).
	 */
	public Line extended(float startFraction, float endFraction){
		double dx = end.x - start.x;
		double dy = end.y - start.y;
		return new Line(start.x - dx * startFraction, start.y - dy * startFraction,
						end.x   + dx * endFraction,   end.y   + dy * endFraction);
	}

	// Binary operations

	/**
	 * Returns a new line which bisects the angle between the two given lines. The resulting line extends to the
	 * boundary of the quadrilateral formed by the given lines' start and end points; as such it is a <i>bimedian</i> of
	 * that quadrilateral.
	 * @param l One of the lines from which the returned line is to be equidistant.
	 * @param m The other line from which the returned line is to be equidistant.
	 * @return The resulting line.
	 */
	public static Line equidistant(Line l, Line m){
		l = Math.abs(l.gradient()) > 1 ? l.yRectified() : l.xRectified();
		m = Math.abs(m.gradient()) > 1 ? m.yRectified() : m.xRectified();
		return new Line((l.start.x + m.start.x) / 2, (l.start.y + m.start.y) / 2,
				(l.end.x + m.end.x) / 2, (l.end.y + m.end.y) / 2);
	}

	/** Returns the angle between the two given lines, according to their direction. The returned angle will be between
	 * 0 and pi radians. */
	public static double angleBetween(Line l, Line m){
		double angleDiff = Math.abs(l.angle() - m.angle());
		return Math.min(angleDiff, 2*Math.PI - angleDiff);
	}

	/** Returns the acute angle between the two given lines. */
	public static double acuteAngleBetween(Line l, Line m){
		double angleDiff = angleBetween(l, m);
		return Math.min(angleDiff, Math.PI - angleDiff);
	}

	/** Returns the obtuse angle between the two given lines. */
	public static double obtuseAngleBetween(Line l, Line m){
		double angleDiff = angleBetween(l, m);
		return Math.max(angleDiff, Math.PI - angleDiff);
	}

	/** Returns the point where the two given lines intersect, or null if they do not intersect or are coincident. */
	public static Point intersection(Line l, Line m){

		// Some sort of dark magic with matrix determinants, I don't know how it avoids divide by 0 issues for vertical
		// lines without resorting to trig but it does
		// https://rosettacode.org/wiki/Find_the_intersection_of_two_lines#Java (for some reason this has the dxs backwards)
		// https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection#Given_two_points_on_each_line
		// https://stackoverflow.com/questions/563198/how-do-you-detect-where-two-line-segments-intersect

		// Non-parameteric version (better for infinite lines)
//		double dy1 = l.end.y - l.start.y;
//		double dx1 = l.end.x - l.start.x;
//		double c1 = dy1 * l.start.x - dx1 * l.start.y;
//
//		double dy2 = m.end.y - m.start.y;
//		double dx2 = m.end.x - m.start.x;
//		double c2 = dy2 * m.start.x - dx2 * m.start.y;
//
//		double det = dy2 * dx1 - dy1 * dx2;
//		if(det == 0) return null;
//		return new Point((dx1 * c2 - dx2 * c1) / det, (dy1 * c2 - dy2 * c1) / det);

		// Parametric version (better for lines of finite length)
		double dy1 = l.end.y - l.start.y;
		double dx1 = l.end.x - l.start.x;

		double dy2 = m.end.y - m.start.y;
		double dx2 = m.end.x - m.start.x;

		// The imaginary third line connects the start points of both lines
		double dy3 = m.start.y - l.start.y;
		double dx3 = m.start.x - l.start.x;

		double det = dy2 * dx1 - dy1 * dx2;
		if(det == 0) return null; // Lines are parallel or coincident

		// t is the fraction along line l, u is the fraction along line m
		// Therefore both t and u must be between 0 and 1 (inclusive) if the lines intersect
		double t = (dx3 * dy2 - dy3 * dx2) / det;
		double u = (dx3 * dy1 - dy3 * dx1) / det;

		if(t < 0 || t > 1 || u < 0 || u > 1) return null; // Intersection is beyond the end of one or both of the lines

		return new Point(l.start.x + t * dx1, l.start.y + t * dy1);

		// Old simultaneous equations (linear algebra) version

		// Standard linear algebra should be avoided because it is tied to the coordinate system, so vertical and
		// near-vertical lines must be accounted for separately to avoid doubles overflowing to infinity
		// https://stackoverflow.com/questions/4543506/algorithm-for-intersection-of-2-lines

//		l = l.xRectified();
//		m = m.xRectified();
//
//		double lGradient = l.gradient();
//		double mGradient = m.gradient();
//
//		if(lGradient == mGradient) return null; // The lines are parallel or coincident
//
//		// Simultaneous equations yay
//		double x = (l.start.y - m.start.y + mGradient * m.start.x - lGradient * l.start.x) / (mGradient - lGradient);
//		double y = l.start.y + lGradient * (x - l.start.x);
//
//		if(x < l.start.x || x > l.end.x || x < m.start.x || x > m.end.x) return null; // Intersection is beyond the end
//
//		return new Point(x, y);

		// I did figure out a third way of doing it using trig, it's probably horribly inefficient but it avoids
		// issues with infinity by working with angles instead of gradients, which is kinda neat

	}

}
