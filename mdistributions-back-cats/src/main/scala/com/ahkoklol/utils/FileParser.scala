package com.ahkoklol.utils

import cats.effect.IO
import org.apache.poi.ss.usermodel.{Cell, CellType, WorkbookFactory}
import java.io.File
import scala.jdk.CollectionConverters.*

object FileParser:

  def parse(file: File, filename: String): IO[List[String]] = 
    val lowerName = filename.toLowerCase
    if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
       parseExcel(file)
    } else if (lowerName.endsWith(".csv")) {
       parseCsv(file)
    } else {
       IO.raiseError(new Exception(s"Unsupported file format: $filename. Please use .csv or .xlsx"))
    }

  // Logic moved here from the old ExcelParser
  private def parseExcel(file: File): IO[List[String]] = IO.blocking {
    val workbook = WorkbookFactory.create(file)
    val sheet = workbook.getSheetAt(0) 
    
    val emails = sheet.iterator().asScala.flatMap { row =>
      val cell = row.getCell(0) 
      if (cell != null && cell.getCellType == CellType.STRING) {
        val value = cell.getStringCellValue.trim
        if (value.contains("@")) Some(value) else None
      } else {
        None
      }
    }.toList

    workbook.close()
    emails
  }

  private def parseCsv(file: File): IO[List[String]] = IO.blocking {
    val source = scala.io.Source.fromFile(file)
    try {
      source.getLines()
        .map(_.split(",")(0).trim) // Assumes email is in the first column
        .filter(_.contains("@"))
        .toList
    } finally {
      source.close()
    }
  }