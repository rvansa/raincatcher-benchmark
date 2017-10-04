package com.feedhenry.raincatcher

object Options {
  // directory where Gatling data will be stored. By default a temp dir will be created
  val testDir = System.getProperty("test.dir")
  val testRampUp = java.lang.Long.getLong("test.rampup", 10)
  val testDuration = java.lang.Long.getLong("test.duration", 30)
  val mobileUsers = java.lang.Integer.getInteger("test.users", 10)
  val workordersPerSec  = System.getProperty("test.workorders.per.sec", "1.0").toDouble
  // probability that user decides to do a modification (start filling out or complete a workorder)
  val modProbability = System.getProperty("test.mod.prob", "0.5").toDouble
  val syncPeriod = java.lang.Integer.getInteger("test.sync.period", 10)
}
