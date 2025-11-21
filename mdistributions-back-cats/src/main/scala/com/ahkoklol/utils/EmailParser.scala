package com.ahkoklol.utils

import cats.effect.IO
import org.apache.poi.ss.usermodel.{Cell, CellType, WorkbookFactory}
import java.io.File
import scala.jdk.CollectionConverters.*

object ExcelParser:
  def parse(file: File): IO[List[String]] = IO.blocking {
    val workbook = WorkbookFactory.create(file)
    val sheet = workbook.getSheetAt(0) // Read first sheet
    
    val emails = sheet.iterator().asScala.flatMap { row =>
      val cell = row.getCell(0) // Read first column (A)
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