package com.eqot.xray;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@Xray(Sample.class)
public class SampleTest {
    @Test
    public void add() throws Exception {
        final Sample$Xray sample = new Sample$Xray();
        final int result = sample.add(1, 2);
        assertEquals(result, 3);
    }
}