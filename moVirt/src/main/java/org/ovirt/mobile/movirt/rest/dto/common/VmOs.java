package org.ovirt.mobile.movirt.rest.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VmOs {
    public String type;
}
