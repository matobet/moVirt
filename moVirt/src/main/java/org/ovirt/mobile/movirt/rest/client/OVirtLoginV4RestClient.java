package org.ovirt.mobile.movirt.rest.client;

import org.androidannotations.rest.spring.annotations.Accept;
import org.androidannotations.rest.spring.annotations.Path;
import org.androidannotations.rest.spring.annotations.Post;
import org.androidannotations.rest.spring.annotations.RequiresHeader;
import org.androidannotations.rest.spring.annotations.Rest;
import org.androidannotations.rest.spring.api.MediaType;
import org.androidannotations.rest.spring.api.RestClientHeaders;
import org.androidannotations.rest.spring.api.RestClientRootUrl;
import org.androidannotations.rest.spring.api.RestClientSupport;
import org.ovirt.mobile.movirt.rest.client.errorhandler.LoginErrorHandler;
import org.ovirt.mobile.movirt.rest.dto.v4.Token;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.ovirt.mobile.movirt.rest.RestHelper.ACCEPT_ENCODING;

@Rest(converters = MappingJackson2HttpMessageConverter.class, responseErrorHandler = LoginErrorHandler.class)
@Accept(MediaType.APPLICATION_JSON)
@RequiresHeader({ACCEPT_ENCODING})
public interface OVirtLoginV4RestClient extends RestClientRootUrl, RestClientHeaders, RestClientSupport {

    @Post("/sso/oauth/token?grant_type=password&scope=ovirt-app-api&username={username}&password={password}")
    @Accept(MediaType.APPLICATION_JSON)
    Token login(@Path String username, @Path String password);
}
