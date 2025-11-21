package com.ahkoklol.integrations

import cats.effect.IO
import com.ahkoklol.config.SmtpConfig // CHANGED: Import SmtpConfig instead of AppConfig
import java.util.Properties
import javax.mail.*
import javax.mail.internet.*

trait SmtpMailer:
  def send(to: String, subject: String, body: String): IO[Unit]

object SmtpMailer:
  def make(config: SmtpConfig): SmtpMailer = new SmtpMailer:
    
    val props = new Properties()
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true") 
    props.put("mail.smtp.host", config.host)
    props.put("mail.smtp.port", config.port.toString)

    override def send(to: String, subject: String, body: String): IO[Unit] = IO.blocking {
      val session = Session.getInstance(props, new Authenticator {
        override def getPasswordAuthentication: PasswordAuthentication =
          new PasswordAuthentication(config.user, config.pass)
      })

      val message = new MimeMessage(session)
      message.setFrom(new InternetAddress(config.user))
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to).asInstanceOf[Array[Address]])
      message.setSubject(subject)
      message.setText(body)

      Transport.send(message)
    }