/* ****************************************
 * $Id$
 * SF bug 2539590:
 *   SF_SWITCH_FALLTHROUGH reports incorrect warning message
 *   when thrown due to missing default case
 *
 * JVM:  1.6.0 (OS X, x86)
 * FBv:  1.3.8-dev-20090128
 *
 * Test case based on example code from bug report
 * **************************************** */

package sfBugs;

import edu.umd.cs.findbugs.annotations.ExpectWarning;
import edu.umd.cs.findbugs.annotations.NoWarning;

public class Bug2539590 {

	public static void noFallthroughMethodNoDefault(int which) {
		switch (which) {
		case 0:
			doSomething();
			break;
		}
	}

	@NoWarning("SF")
	public static void noFallthroughMethod(int which) {
		switch (which) {
		case 0:
			doSomething();
			break;
		default:
		}
	}
	public static void noFallthroughMethod2(int which) {
		switch (which) {
		case 0:
			doSomething();
			break;
		default:
			break;
		}
	}

	/*
	 * Behavior at filing: warning message thrown for fallthrough in switch
	 * statement does not mention missing default case
	 * 
	 * warning thrown => M D SF_SWITCH_FALLTHROUGH SF: Switch statement found in
	 * \ sfBugs.Bug2539590.fallthroughMethod(int) where one case falls \ through
	 * to the next case At Bug2539590.java:[lines 33-35]
	 */
	@NoWarning("SF_SWITCH_FALLTHROUGH")
	@ExpectWarning("SF_SWITCH_NO_DEFAULT")
	public static void fallthroughMethodNoDefault(int which) {
		switch (which) {
		case 0:
			doSomething();
		}
	}



	public static void fallthroughMethod(int which) {
		switch (which) {
		case 0:
			doSomething();
		default:
			break;
		}
	}

	@ExpectWarning("SF")
	public static int fallthroughMethodNoDefaultClobber(int which) {
		int result = 0;
		switch (which) {
		case 0:
			doSomething();
			result = 1;
		}
		result = 2;
		return result;
	}


	@ExpectWarning("SF")
	public static int fallthroughMethodClobber(int which) {
		int result = 0;
		switch (which) {
		case 0:
			doSomething();
			result = 1;
		default:
			result = 2;
		}
		return result;
	}


	@ExpectWarning("SF")
	public static int fallthroughMethodToss(int which) {
		int result;
		switch (which) {
		case 0:
			doSomething();
			result = 1;
			break;
		case 1:
			result = 2;
		default:
			throw new IllegalArgumentException();
		}
		return result;
	}
	public static void doSomething() {
		System.out.println("Hello world!");
		return;
	}
}