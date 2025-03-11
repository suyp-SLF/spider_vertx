package com.example.starter_vertx;

import io.vertx.core.Launcher;

public class Main extends Launcher {
  public static void main(String[] args){
    new Main().dispatch(new String[]{"run", MainVerticle.class.getName()});
  }
}
