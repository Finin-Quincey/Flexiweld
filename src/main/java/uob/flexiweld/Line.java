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

	/** Returns the perpendicular distance from this line to the given point. */
	public double distanceTo(Point point){
		Line hypot = new Line(this.start, point);
		return hypot.length() * Math.sin(acuteAngleBetween(this, hypot));
	}

	/** Returns the point on this line that is nearest to the given point. */
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
		l = l.xRectified();
		m = m.xRectified();
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

}
