package com.flowpanel.ai;

import java.util.List;

/** Structured outputs can add semantic checks on top of the JSON schema; errors trigger the gateway's retry. */
public interface ValidatableOutput {

    List<String> validationErrors();
}
