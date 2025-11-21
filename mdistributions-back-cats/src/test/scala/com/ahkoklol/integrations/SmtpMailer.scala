package com.ahkoklol.integrations

import cats.effect.IO
import java.util.Properties
import javax.mail.*
import javax.mail.internet.*

trait SmtpMailer:
  def send(to: String, subject: String, body: String): IO[Unit]

object SmtpMailer:
  def make(user: String, appPassword: String): SmtpMailer = new SmtpMailer:
    
    val props = new Properties()
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", "smtp.gmail.com")
    props.put("mail.smtp.port", "587")

    override def send(to: String, subject: String, body: String): IO[Unit] = IO.blocking {
      val session = Session.getInstance(props, new Authenticator {
        override def getPasswordAuthentication: PasswordAuthentication =
          new PasswordAuthentication(user, appPassword)
      })

      val message = new MimeMessage(session)
      message.setFrom(new InternetAddress(user))
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to).asInstanceOf[Array[Address]])
      message.setSubject(subject)
      message.setText(body) // Set content

      Transport.send(message)
    }