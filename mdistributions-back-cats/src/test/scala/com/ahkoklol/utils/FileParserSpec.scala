package com.ahkoklol.utils

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.{File, PrintWriter}

class FileParserSpec extends AnyFlatSpec with Matchers {

  // Helper to create a temp CSV file
  def createTempCsv(content: String): File = {
    val file = File.createTempFile("test", ".csv")
    val writer = new PrintWriter(file)
    writer.write(content)
    writer.close()
    file.deleteOnExit()
    file
  }

  "FileParser" should "parse a valid CSV file" in {
    val content =
      """email,name
        |alice@test.com,Alice
        |bob@test.com,Bob
        |""".stripMargin
    
    val file = createTempCsv(content)
    // We pass the filename explicitly as the 2nd argument
    val emails = FileParser.parse(file, "test.csv").unsafeRunSync()
    
    emails should contain theSameElementsAs List("alice@test.com", "bob@test.com")
  }

  it should "ignore lines without valid emails" in {
    val content =
      """header
        |invalid-email
        |valid@test.com
        |""".stripMargin
        
    val file = createTempCsv(content)
    val emails = FileParser.parse(file, "data.csv").unsafeRunSync()
    
    emails should have size 1
    emails.head shouldBe "valid@test.com"
  }

  it should "fail for unsupported file extensions" in {
    val file = File.createTempFile("test", ".txt")
    file.deleteOnExit()
    
    assertThrows[Exception] {
      FileParser.parse(file, "test.txt").unsafeRunSync()
    }
  }
}