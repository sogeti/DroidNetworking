package com.sogeti.droidnetworking.test.unit;

import com.sogeti.droidnetworking.external.MD5;

import junit.framework.TestCase;

public class MD5Test extends TestCase {
	public void testEncodeStringTC1() {
		String encodedString = MD5.encodeString("droidnetworking");
		assertTrue(encodedString.equals("1e9b615a3eded933452c1ed4c96f1f41"));
	}
	
	public void testEncodeStringTC2() {
		String encodedString = MD5.encodeString("droidnetworking123");
		assertTrue(encodedString.equals("56d20fd3c6d198b3ac6259af6cc8e6bb"));
	}
}
