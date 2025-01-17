/*
 * To the extent possible under law, the author has dedicated all copyright
 * and related and neighboring rights to this software to the public domain
 * worldwide. This software is distributed without any warranty.
 *
 * See <http://creativecommons.org/publicdomain/zero/1.0/>
 */

package com.unascribed.flexver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implements FlexVer, a SemVer-compatible intuitive comparator for free-form versioning strings as
 * seen in the wild. It's designed to sort versions like people do, rather than attempting to force
 * conformance to a rigid and limited standard. As such, it imposes no restrictions. Comparing two
 * versions with differing formats will likely produce nonsensical results (garbage in, garbage out),
 * but best effort is made to correct for basic structural changes, and versions of differing length
 * will be parsed in a logical fashion.
 */
public class FlexVerComparator {
	
	/**
	 * Parse the given strings as freeform version strings, and compare them according to FlexVer.
	 * @param a the first version string
	 * @param b the second version string
	 * @return {@code 0} if the two versions are equal, a negative number if {@code a < b}, or a positive number if {@code a > b}
	 */
	public static int compare(String a, String b) {
		List<VersionComponent> ad = decompose(a);
		List<VersionComponent> bd = decompose(b);
		for (int i = 0; i < Math.max(ad.size(), bd.size()); i++) {
			int c = get(ad, i).compareTo(get(bd, i));
			if (c != 0) return c;
		}
		return 0;
	}
	

	private static final VersionComponent NULL = new VersionComponent(new int[0]) {
		@Override
		public int compareTo(VersionComponent other) { return other == NULL ? 0 : -other.compareTo(this); }
	};
	
	// @VisibleForTesting
	static abstract class VersionComponent {
		private final int[] codepoints;

		public VersionComponent(int[] codepoints) {
			this.codepoints = codepoints;
		}
		
		public int[] codepoints() {
			return codepoints;
		}
		
		public abstract int compareTo(VersionComponent that);
		
		@Override
		public String toString() {
			return new String(codepoints, 0, codepoints.length);
		}
		
	}
	
	// @VisibleForTesting
	static class LiteralVersionComponent extends VersionComponent {
		public LiteralVersionComponent(int[] codepoints) { super(codepoints); }

		@Override
		public int compareTo(VersionComponent that) {
			if (that == NULL) return 1;
			int[] a = this.codepoints();
			int[] b = that.codepoints();
			
			for (int i = 0; i < Math.min(a.length, b.length); i++) {
				int c1 = a[i];
				int c2 = b[i];
				if (c1 != c2) return c1 - c2;
			}
			
			return a.length - b.length;
		}
	}
	
	// @VisibleForTesting
	static class SemVerPrereleaseVersionComponent extends LiteralVersionComponent {
		public SemVerPrereleaseVersionComponent(int[] codepoints) { super(codepoints); }
		
		@Override
		public int compareTo(VersionComponent that) {
			if (that == NULL) return -1; // opposite order
			return super.compareTo(that);
		}
		
	}
	
	// @VisibleForTesting
	static class NumericVersionComponent extends VersionComponent {
		public NumericVersionComponent(int[] codepoints) { super(codepoints); }
		
		@Override
		public int compareTo(VersionComponent that) {
			if (that == NULL) return 1;
			if (that instanceof NumericVersionComponent) {
				int[] a = this.codepoints();
				int[] b = that.codepoints();
				a = removeLeadingZeroes(a);
				b = removeLeadingZeroes(b);
				if (a.length != b.length) return Integer.compare(a.length, b.length);
				for (int i = 0; i < a.length; i++) {
					int ad = Character.digit(a[i], 10);
					int bd = Character.digit(b[i], 10);
					if (ad != bd) return ad-bd;
				}
				return 0;
			}
			return toString().compareTo(that.toString());
		}
		
		private int[] removeLeadingZeroes(int[] a) {
			if (a.length == 1) return a;
			int i = 0;
			while (i < a.length && Character.digit(a[i], 10) == 0) {
				i++;
			}
			return Arrays.copyOfRange(a, i, a.length);
		}

	}
	
	/*
	 * Break apart a string into intuitive version components, by splitting it where a run of
	 * characters changes from numeric to non-numeric.
	 */
	// @VisibleForTesting
	static List<VersionComponent> decompose(String str) {
		if (str.isEmpty()) return Collections.emptyList();
		boolean lastWasNumber = isAsciiDigit(str.codePointAt(0));
		int totalCodepoints = str.codePointCount(0, str.length());
		int[] accum = new int[totalCodepoints];
		List<VersionComponent> out = new ArrayList<>();
		int j = 0;
		for (int i = 0; i < str.length(); i++) {
			if (i > 0 && Character.isHighSurrogate(str.charAt(i-1)) && Character.isLowSurrogate(str.charAt(i))) continue;
			int cp = str.codePointAt(i);
			if (cp == '+') break; // remove appendices
			boolean number = isAsciiDigit(cp);
			if (number != lastWasNumber) {
				out.add(createComponent(lastWasNumber, accum, j));
				accum = new int[totalCodepoints];
				j = 0;
				lastWasNumber = number;
			}
			accum[j] = cp;
			j++;
		}
		out.add(createComponent(lastWasNumber, accum, j));
		return out;
	}

	private static boolean isAsciiDigit(int cp) {
		return cp >= '0' && cp <= '9';
	}

	private static VersionComponent createComponent(boolean number, int[] s, int j) {
		s = Arrays.copyOfRange(s, 0, j);
		if (number) {
			return new NumericVersionComponent(s);
		} else if (s.length > 1 && s[0] == '-') {
			return new SemVerPrereleaseVersionComponent(s);
		} else {
			return new LiteralVersionComponent(s);
		}
	}

	private static VersionComponent get(List<VersionComponent> li, int i) {
		return i >= li.size() ? NULL : li.get(i);
	}

}
