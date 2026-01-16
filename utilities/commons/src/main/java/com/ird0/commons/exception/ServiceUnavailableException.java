package com.ird0.commons.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BaseException {

  public ServiceUnavailableException(String serviceName) {
    super(serviceName + " is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE);
  }

  public ServiceUnavailableException(String serviceName, Throwable cause) {
    super(serviceName + " is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE, cause);
  }
}
