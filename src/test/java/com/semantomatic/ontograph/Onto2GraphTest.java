package com.semantomatic.ontograph;

import org.testng.annotations.Test;

public class Onto2GraphTest {

  @Test
  public void loadOntoIntoNeoTest() throws Exception {
    Onto2Graph og = new Onto2Graph();
    og.loadOntoIntoNeo();
  }
}
