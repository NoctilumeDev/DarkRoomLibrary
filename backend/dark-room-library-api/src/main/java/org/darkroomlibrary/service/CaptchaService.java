package org.darkroomlibrary.service;

import java.util.Map;

public interface CaptchaService {

    Map<String, String> generate();

    boolean verify(String captchaId, Integer answer);
}
