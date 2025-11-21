package com.ahkoklol.services

import com.ahkoklol.IntegrationSpec
import com.ahkoklol.domain.{Email, EmailError}
import com.ahkoklol.repositories.{EmailRepository, UserRepository}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.{File, FileOutputStream}
import java.util.UUID

class EmailServiceSpec extends IntegrationSpec {

  lazy val userRepo = UserRepository.make(xa)
  lazy val emailRepo = EmailRepository.make(xa)
  lazy val emailService = EmailService.make(emailRepo)

  def createUser(): UUID = {
    val id = UUID.randomUUID()
    val u = com.ahkoklol.domain.User(
      id, "hash", None, None, s"user-$id@example.com", None
    )
    userRepo.create(u).unwrap
    id
  }

  // --- Helper to create a real .xlsx file for testing ---
  def createTempExcel(emails: List[String]): File = {
    val file = File.createTempFile("test-emails", ".xlsx")
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet("Emails")

    // Write emails to Column A
    emails.zipWithIndex.foreach { case (email, index) =>
      val row = sheet.createRow(index)
      val cell = row.createCell(0)
      cell.setCellValue(email)
    }

    val fos = new FileOutputStream(file)
    workbook.write(fos)
    fos.close()
    workbook.close()
    
    file.deleteOnExit() // Auto-cleanup
    file
  }

  "EmailService" should "create an email campaign from an Excel file" in {
    val userId = createUser()
    val subject = "Marketing Campaign"
    val body = "Hello!"
    
    // Create a dummy Excel file with 3 emails
    val recipients = List("alice@test.com", "bob@test.com", "charlie@test.com")
    val excelFile = createTempExcel(recipients)

    // Action: Passed excelFile.getName as the new 'filename' argument
    val email = emailService.create(userId, subject, body, excelFile, excelFile.getName).unwrap

    // Assertions
    email.userId shouldBe userId
    email.subject shouldBe subject
    email.recipients should contain theSameElementsAs recipients
    email.recipients should have size 3

    // Verify persistence
    val fetched = emailService.find(userId, email.id).unwrap
    fetched.map(_.recipients) shouldBe Right(recipients)
  }

  it should "list all emails for a user" in {
    val userId = createUser()
    val file = createTempExcel(List("a@a.com"))
    
    // Action: Passed file.getName
    emailService.create(userId, "Subj 1", "Body 1", file, file.getName).unwrap
    emailService.create(userId, "Subj 2", "Body 2", file, file.getName).unwrap

    val list = emailService.findAll(userId).unwrap
    list should have size 2
  }

  it should "prevent accessing someone else's email (AccessDenied)" in {
    val aliceId = createUser()
    val bobId = createUser()
    val file = createTempExcel(List("secret@test.com"))

    // Action: Passed file.getName
    val aliceEmail = emailService.create(aliceId, "Secret", "Body", file, file.getName).unwrap

    // Bob tries to read it
    val result = emailService.find(bobId, aliceEmail.id).unwrap
    result shouldBe Left(EmailError.AccessDenied)
  }

  it should "return EmailNotFound for non-existent ID" in {
    val userId = createUser()
    val result = emailService.find(userId, UUID.randomUUID()).unwrap
    result shouldBe Left(EmailError.EmailNotFound)
  }

  it should "delete an email only if owned by user" in {
    val userId = createUser()
    val file = createTempExcel(List("delete@me.com"))
    // Action: Passed file.getName
    val email = emailService.create(userId, "To Delete", "Body", file, file.getName).unwrap

    // Success case
    emailService.delete(userId, email.id).unwrap shouldBe Right(())
    
    // Verify it's gone
    emailService.find(userId, email.id).unwrap shouldBe Left(EmailError.EmailNotFound)
  }
}