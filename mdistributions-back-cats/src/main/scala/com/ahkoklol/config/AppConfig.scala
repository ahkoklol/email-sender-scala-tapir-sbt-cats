package com.ahkoklol.config

import pureconfig.*
import pureconfig.generic.derivation.default.*

case class SmtpConfig(
    user: String,
    pass: String,
    host: String = "smtp.gmail.com",
    port: Int = 587
) derives ConfigReader

case class AppConfig(
    smtp: SmtpConfig,
    //db config here
) derives ConfigReader

object AppConfig:
  def load: AppConfig = ConfigSource.default.loadOrThrow[AppConfig]