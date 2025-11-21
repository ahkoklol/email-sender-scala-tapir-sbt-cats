package com.ahkoklol.integrations

import cats.effect.IO
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.{Sheets, SheetsScopes}
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.FileInputStream
import java.util.Collections
import scala.jdk.CollectionConverters.*

trait GoogleSheets:
  def fetchEmails(sheetUrl: String): IO[List[String]]

object GoogleSheets:
  def make(credentialsPath: String = "credentials.json"): IO[GoogleSheets] = IO {
    val transport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = GsonFactory.getDefaultInstance
    
    val credentials = GoogleCredentials
      .fromStream(new FileInputStream(credentialsPath))
      .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY))

    val service = new Sheets.Builder(transport, jsonFactory, new HttpCredentialsAdapter(credentials))
      .setApplicationName("EmailSenderApp")
      .build()

    new GoogleSheets {
      override def fetchEmails(sheetUrl: String): IO[List[String]] = IO.blocking {
        // 1. Extract Sheet ID from URL
        // Pattern: https://docs.google.com/spreadsheets/d/SHEET_ID/edit...
        val idPattern = "/d/([a-zA-Z0-9-_]+)".r
        val spreadsheetId = idPattern.findFirstMatchIn(sheetUrl).map(_.group(1)) match
          case Some(id) => id
          case None     => throw new Exception("Invalid Google Sheet URL")

        // 2. Read Column A (Assumes emails are in the first column)
        val range = "A:A" 
        val response = service.spreadsheets().values().get(spreadsheetId, range).execute()
        val values = Option(response.getValues).map(_.asScala.toList).getOrElse(Nil)

        // 3. Flatten and filter valid emails
        values.flatMap(_.asScala.headOption.map(_.toString)).filter(_.contains("@"))
      }
    }
  }