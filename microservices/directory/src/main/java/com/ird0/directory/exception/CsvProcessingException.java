package com.ird0.directory.exception;

import com.ird0.commons.exception.BaseException;
import org.springframework.http.HttpStatus;

public class CsvProcessingException extends BaseException {

  public CsvProcessingException(String message) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  public CsvProcessingException(String message, Throwable cause) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
  }
}
