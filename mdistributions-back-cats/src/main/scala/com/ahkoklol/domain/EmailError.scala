package com.ahkoklol.domain

enum EmailError extends Exception:
  case EmailNotFound
  case AccessDenied