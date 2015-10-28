/**
 * Copyright (c) 2012, Oliver Kleine, Institute of Telematics, University of Luebeck
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source messageCode must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uzl.itm.ncoap.message;

import com.google.common.base.Supplier;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.primitives.Longs;
import de.uzl.itm.ncoap.communication.dispatching.client.Token;
import de.uzl.itm.ncoap.communication.identification.EndpointID;
import de.uzl.itm.ncoap.message.options.*;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.*;


/**
 * This class is the base class for inheriting subtypes, e.g. requests and responses. This abstract class provides the
 * cut-set in terms of functionality of {@link CoapRequest} and {@link CoapResponse}.
 *
 * @author Oliver Kleine
 */
public abstract class CoapMessage {

    /**
     * The CoAP protocol version (1)
     */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * The default character set for {@link CoapMessage}s (UTF-8)
     */
    public static final Charset CHARSET = Charset.forName("UTF-8");

    /**
     * Internal constant to indicate that the message ID was not yet set (-1)
     */
    public static final int UNDEFINED_MESSAGE_ID = -1;

    /**
     * The maximum length of the byte array that backs the {@link Token} of {@link CoapMessage} (8)
     */
    public static final int MAX_TOKEN_LENGTH = 8;

    private static Logger log = LoggerFactory.getLogger(CoapMessage.class.getName());

    private static final String WRONG_OPTION_TYPE = "Option no. %d is no option of type %s";
    private static final String OPTION_NOT_ALLOWED_WITH_MESSAGE_TYPE = "Option no. %d is not allowed with " +
            "message type %s";
    private static final String OPTION_ALREADY_SET = "Option no. %d is already set and is only allowed once per " +
            "message";

    private static final String DOES_NOT_ALLOW_CONTENT = "CoAP messages with code %s do not allow payload.";
    private static final String EXCLUDES = "Already contained option no. %d excludes option no. %d";
//    private static final String OUT_OF_ALLOWED_RANGE = "Given value length (%d) is out of allowed range " +
//            "for option no. %d (min: %d, max; %d).";

    private static final int ONCE       = 1;
    private static final int MULTIPLE   = 2;

    private static HashBasedTable<Integer, Integer, Integer> optionOccurenceConstraints = HashBasedTable.create();
    static{
        //Requests
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.URI_HOST,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.URI_PORT,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.URI_PATH,           MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.URI_QUERY,          MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.PROXY_URI,          ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.PROXY_SCHEME,       ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.ACCEPT,             MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.ETAG,               MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.OBSERVE,            ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.BLOCK2,             ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.GET.getNumber(),      OptionValue.Name.ENDPOINT_ID_1,      ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.URI_HOST,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.URI_PORT,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.URI_PATH,           MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.URI_QUERY,          MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.PROXY_URI,          ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.PROXY_SCHEME,       ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.CONTENT_FORMAT,     ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.BLOCK2,             ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.POST.getNumber(),     OptionValue.Name.ENDPOINT_ID_1,      ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.URI_HOST,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.URI_PORT,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.URI_PATH,           MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.URI_QUERY,          MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.PROXY_URI,          ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.PROXY_SCHEME,       ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.CONTENT_FORMAT,     ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.IF_MATCH,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.IF_NONE_MATCH,      ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.BLOCK2,             ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PUT.getNumber(),      OptionValue.Name.ENDPOINT_ID_1,      ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.DELETE.getNumber(),   OptionValue.Name.URI_HOST,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.DELETE.getNumber(),   OptionValue.Name.URI_PORT,           ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.DELETE.getNumber(),   OptionValue.Name.URI_PATH,           MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.DELETE.getNumber(),   OptionValue.Name.URI_QUERY,          MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.DELETE.getNumber(),   OptionValue.Name.PROXY_URI,          ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.DELETE.getNumber(),   OptionValue.Name.PROXY_SCHEME,       ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.DELETE.getNumber(),   OptionValue.Name.ENDPOINT_ID_1,      ONCE);

        //Response success (2.x)
        optionOccurenceConstraints.put(MessageCode.Name.CREATED_201.getNumber(),  OptionValue.Name.ETAG,               ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CREATED_201.getNumber(),  OptionValue.Name.OBSERVE,            ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CREATED_201.getNumber(),  OptionValue.Name.LOCATION_PATH,      MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.CREATED_201.getNumber(),  OptionValue.Name.LOCATION_QUERY,     MULTIPLE);
        optionOccurenceConstraints.put(MessageCode.Name.CREATED_201.getNumber(),  OptionValue.Name.CONTENT_FORMAT,     ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CREATED_201.getNumber(),  OptionValue.Name.BLOCK2,             ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CREATED_201.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,      ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.DELETED_202.getNumber(),  OptionValue.Name.CONTENT_FORMAT,     ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.DELETED_202.getNumber(),  OptionValue.Name.BLOCK2,             ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.DELETED_202.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,      ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.VALID_203.getNumber(),    OptionValue.Name.OBSERVE,            ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.VALID_203.getNumber(),    OptionValue.Name.ETAG,               ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.VALID_203.getNumber(),    OptionValue.Name.MAX_AGE,            ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.VALID_203.getNumber(),    OptionValue.Name.CONTENT_FORMAT,     ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.VALID_203.getNumber(),    OptionValue.Name.ENDPOINT_ID_1,      ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.VALID_203.getNumber(),    OptionValue.Name.ENDPOINT_ID_2,      ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.CHANGED_204.getNumber(),  OptionValue.Name.CONTENT_FORMAT,     ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CHANGED_204.getNumber(),  OptionValue.Name.BLOCK2,             ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CHANGED_204.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,      ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.CONTENT_205.getNumber(),  OptionValue.Name.OBSERVE,            ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CONTENT_205.getNumber(),  OptionValue.Name.CONTENT_FORMAT,     ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CONTENT_205.getNumber(),  OptionValue.Name.MAX_AGE,            ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CONTENT_205.getNumber(),  OptionValue.Name.ETAG,               ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CONTENT_205.getNumber(),  OptionValue.Name.BLOCK2,             ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CONTENT_205.getNumber(),  OptionValue.Name.ENDPOINT_ID_1,      ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.CONTENT_205.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,      ONCE);

        //Client errors (4.x)
        optionOccurenceConstraints.put(MessageCode.Name.BAD_REQUEST_400.getNumber(),  OptionValue.Name.MAX_AGE,        ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.BAD_REQUEST_400.getNumber(),  OptionValue.Name.CONTENT_FORMAT, ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.BAD_REQUEST_400.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,  ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.UNAUTHORIZED_401.getNumber(), OptionValue.Name.MAX_AGE,        ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.UNAUTHORIZED_401.getNumber(), OptionValue.Name.CONTENT_FORMAT, ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.UNAUTHORIZED_401.getNumber(), OptionValue.Name.ENDPOINT_ID_2,  ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.BAD_OPTION_402.getNumber(),  OptionValue.Name.MAX_AGE,         ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.BAD_OPTION_402.getNumber(),  OptionValue.Name.CONTENT_FORMAT,  ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.BAD_OPTION_402.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,   ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.FORBIDDEN_403.getNumber(),  OptionValue.Name.MAX_AGE,          ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.FORBIDDEN_403.getNumber(),  OptionValue.Name.CONTENT_FORMAT,   ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.FORBIDDEN_403.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,    ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.NOT_FOUND_404.getNumber(),  OptionValue.Name.MAX_AGE,          ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.NOT_FOUND_404.getNumber(),  OptionValue.Name.CONTENT_FORMAT,   ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.NOT_FOUND_404.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,    ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.METHOD_NOT_ALLOWED_405.getNumber(),  OptionValue.Name.MAX_AGE,        ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.METHOD_NOT_ALLOWED_405.getNumber(),  OptionValue.Name.CONTENT_FORMAT, ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.METHOD_NOT_ALLOWED_405.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,  ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.NOT_ACCEPTABLE_406.getNumber(),  OptionValue.Name.MAX_AGE,        ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.NOT_ACCEPTABLE_406.getNumber(),  OptionValue.Name.CONTENT_FORMAT, ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.NOT_ACCEPTABLE_406.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,  ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.PRECONDITION_FAILED_412.getNumber(),  OptionValue.Name.MAX_AGE,        ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PRECONDITION_FAILED_412.getNumber(),  OptionValue.Name.CONTENT_FORMAT, ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PRECONDITION_FAILED_412.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,  ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.REQUEST_ENTITY_TOO_LARGE_413.getNumber(),  OptionValue.Name.MAX_AGE,         ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.REQUEST_ENTITY_TOO_LARGE_413.getNumber(),  OptionValue.Name.CONTENT_FORMAT,  ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.REQUEST_ENTITY_TOO_LARGE_413.getNumber(),  OptionValue.Name.SIZE_1,          ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.REQUEST_ENTITY_TOO_LARGE_413.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,   ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.UNSUPPORTED_CONTENT_FORMAT_415.getNumber(),  OptionValue.Name.MAX_AGE,         ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.UNSUPPORTED_CONTENT_FORMAT_415.getNumber(),  OptionValue.Name.CONTENT_FORMAT,  ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.UNSUPPORTED_CONTENT_FORMAT_415.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,   ONCE);

        //Server errors (5.x)
        optionOccurenceConstraints.put(MessageCode.Name.INTERNAL_SERVER_ERROR_500.getNumber(),  OptionValue.Name.MAX_AGE,         ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.INTERNAL_SERVER_ERROR_500.getNumber(),  OptionValue.Name.CONTENT_FORMAT,  ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.INTERNAL_SERVER_ERROR_500.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,   ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.NOT_IMPLEMENTED_501.getNumber(),  OptionValue.Name.MAX_AGE,         ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.NOT_IMPLEMENTED_501.getNumber(),  OptionValue.Name.CONTENT_FORMAT,  ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.NOT_IMPLEMENTED_501.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,   ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.BAD_GATEWAY_502.getNumber(),  OptionValue.Name.MAX_AGE,         ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.BAD_GATEWAY_502.getNumber(),  OptionValue.Name.CONTENT_FORMAT,  ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.BAD_GATEWAY_502.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,   ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.GATEWAY_TIMEOUT_504.getNumber(),  OptionValue.Name.MAX_AGE,         ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.GATEWAY_TIMEOUT_504.getNumber(),  OptionValue.Name.CONTENT_FORMAT,  ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.GATEWAY_TIMEOUT_504.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,   ONCE);

        optionOccurenceConstraints.put(MessageCode.Name.PROXYING_NOT_SUPPORTED_505.getNumber(),  OptionValue.Name.MAX_AGE,        ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PROXYING_NOT_SUPPORTED_505.getNumber(),  OptionValue.Name.CONTENT_FORMAT, ONCE);
        optionOccurenceConstraints.put(MessageCode.Name.PROXYING_NOT_SUPPORTED_505.getNumber(),  OptionValue.Name.ENDPOINT_ID_2,  ONCE);
    }


    private int messageType;
    private int messageCode;
    private int messageID;
    private Token token;

    protected SetMultimap<Integer, OptionValue> options;
    private ChannelBuffer content;

    /**
     * Creates a new instance of {@link CoapMessage}.
     *
     * @param messageType the number representing the {@link MessageType} for this {@link CoapMessage}
     * @param messageCode the number representing the {@link MessageCode} for this {@link CoapMessage}
     * @param messageID  the message ID for this {@link CoapMessage}
     * @param token the {@link Token} for this {@link CoapMessage}
     *
     * @throws IllegalArgumentException if one of the given arguments is invalid
     */
    protected CoapMessage(int messageType, int messageCode, int messageID, Token token)
            throws IllegalArgumentException {

        if(!MessageType.Name.isMessageType(messageType))
            throw new IllegalArgumentException("No. " + messageType + " is not corresponding to any message type.");

        if(!MessageCode.Name.isMessageCode(messageCode))
            throw new IllegalArgumentException("No. " + messageCode + " is not corresponding to any message code.");

        this.setMessageType(messageType);
        this.setMessageCode(messageCode);

        log.debug("Set Message Code to {} ({}).", MessageCode.Name.getName(messageCode), messageCode);

        this.setMessageID(messageID);
        this.setToken(token);

        this.options = Multimaps.newSetMultimap(new TreeMap<Integer, Collection<OptionValue>>(),
                LinkedHashSetSupplier.getInstance());

        this.content = ChannelBuffers.EMPTY_BUFFER;

        log.debug("Created CoAP message: {}", this);
    }


    /**
     * Creates a new instance of {@link CoapMessage}. Invocation of this constructor has the same effect as
     * invocation of {@link #CoapMessage(int, int, int, de.uzl.itm.ncoap.communication.dispatching.client.Token)} with
     * <ul>
     *     <li>
     *         message ID: {@link CoapMessage#UNDEFINED_MESSAGE_ID} (to be set automatically by the framework)
     *     </li>
     *     <li>
     *         token: {@link Token#Token(byte[])} with empty byte array.
     *     </li>
     * </ul>
     * @param messageType the number representing the {@link MessageType} for this {@link CoapMessage}
     * @param messageCode the number representing the {@link MessageCode} for this {@link CoapMessage}
     *
     * @throws IllegalArgumentException if one of the given arguments is invalid
     */
    protected CoapMessage(int messageType, int messageCode) throws IllegalArgumentException {
        this(messageType, messageCode, UNDEFINED_MESSAGE_ID, new Token(new byte[0]));
    }


    /**
     * Method to create an empty reset message which is strictly speaking neither a request nor a response
     *
     * @param messageID the message ID of the reset message.
     *
     * @return an instance of {@link CoapMessage} with {@link MessageType.Name#RST}
     *
     * @throws IllegalArgumentException if the given message ID is out of the allowed range
     */
    public static CoapMessage createEmptyReset(int messageID) throws IllegalArgumentException {
        return new CoapMessage(MessageType.Name.RST.getNumber(), MessageCode.Name.EMPTY.getNumber(), messageID,
                new Token(new byte[0])){};
    }


    /**
     * Method to create an empty acknowledgement message which is strictly speaking neither a request nor a response
     *
     * @param messageID the message ID of the acknowledgement message.
     *
     * @return an instance of {@link CoapMessage} with {@link MessageType.Name#ACK}
     *
     * @throws IllegalArgumentException if the given message ID is out of the allowed range
     */
    public static CoapMessage createEmptyAcknowledgement(int messageID) throws IllegalArgumentException {
        return new CoapMessage(MessageType.Name.ACK.getNumber(), MessageCode.Name.EMPTY.getNumber(), messageID,
                new Token(new byte[0])){};
    }


    /**
     * Method to create an empty confirmable message which is considered a PIMG message on application layer, i.e.
     * a message to check if a CoAP endpoints is alive (not only the host but also the CoAP application!).
     *
     * @param messageID the message ID of the acknowledgement message.
     *
     * @return an instance of {@link CoapMessage} with {@link MessageType.Name#CON}
     *
     * @throws IllegalArgumentException if the given message ID is out of the allowed range
     */
    public static CoapMessage createPing(int messageID) throws IllegalArgumentException{
        return new CoapMessage(MessageType.Name.CON.getNumber(), MessageCode.Name.EMPTY.getNumber(), messageID,
                new Token(new byte[0])){};
    }


    /**
     * Sets the message type of this {@link CoapMessage}. Usually there is no need to use this method as the value
     * is either set via constructor parameter (for requests) or automatically by the nCoAP framework (for responses).
     *
     * @param messageType the number representing the message type of this method
     *
     * @throws java.lang.IllegalArgumentException if the given message type is not supported.
     */
    public void setMessageType(int messageType) throws IllegalArgumentException {
        if(!MessageType.Name.isMessageType(messageType))
            throw new IllegalArgumentException("Invalid message type (" + messageType +
                    "). Only numbers 0-3 are allowed.");

        this.messageType = messageType;
    }


    /**
     * Sets the message type of this {@link CoapMessage}. Usually there is no need to use this method as the value
     * is either set via constructor parameter (for requests) or automatically by the nCoAP framework (for responses).
     *
     * @param messageType the {@link de.uzl.itm.ncoap.message.MessageType.Name} to be set
     *
     * @throws java.lang.IllegalArgumentException if the given message type is not supported.
     */
    public void setMessageType(MessageType.Name messageType) throws IllegalArgumentException {
        setMessageType(messageType.getNumber());
    }


    public boolean isPing() {
        return this.messageCode == MessageCode.Name.EMPTY.getNumber() &&
                this.messageType == MessageType.Name.CON.getNumber();
    }

    public boolean isRequest() {
        return MessageCode.isRequest(this.getMessageCode());
    }

    public boolean isResponse() {
        return MessageCode.isResponse(this.getMessageCode());
    }

    /**
     * Adds an option to this {@link CoapMessage}. However, it is recommended to use the options specific methods
     * from {@link CoapRequest} and {@link CoapResponse} to add options. This method is intended for framework internal
     * use.
     *
     * @param optionNumber the number representing the option type
     * @param optionValue the {@link OptionValue} of this option
     *
     * @throws java.lang.IllegalArgumentException if the given option number is unknwon, or if the given value is
     * either the default value or exceeds the defined length limits for options with the given option number
     */
    public void addOption(int optionNumber, OptionValue optionValue) throws IllegalArgumentException {
        this.checkOptionPermission(optionNumber);

//        if(optionNumber == OptionValue.Name.OBSERVE && MessageCode.isRequest(this.getMessageCode())
//            && optionValue.getValue().length > 0){
//
//            throw new IllegalArgumentException(String.format(OUT_OF_ALLOWED_RANGE,
//                    optionValue.getValue().length, 6, 0, 0));
//        }

        for(int containedOption : options.keySet()){
            if(OptionValue.mutuallyExcludes(containedOption, optionNumber))
                throw new IllegalArgumentException(String.format(EXCLUDES, containedOption, optionNumber));
        }

        options.put(optionNumber, optionValue);

        log.debug("Added option (number: {}, value: {})", optionNumber, optionValue.toString());

    }


    /**
     * Adds an string option to this {@link CoapMessage}. However, it is recommended to use the options specific methods
     * from {@link CoapRequest} and {@link CoapResponse} to add options. This method is intended for framework internal
     * use.
     *
     * @param optionNumber the number representing the option type
     * @param value the value of this string option
     *
     * @throws java.lang.IllegalArgumentException if the given option number refers to an unknown option or if the
     * given {@link OptionValue} is not valid, e.g. to long
     */
    protected void addStringOption(int optionNumber, String value) throws IllegalArgumentException {

        if(!(OptionValue.getOptionType(optionNumber) == OptionValue.Type.STRING))
            throw new IllegalArgumentException(String.format(WRONG_OPTION_TYPE, optionNumber, OptionValue.Type.STRING));

        //Add new option to option list
        StringOptionValue option = new StringOptionValue(optionNumber, value);
        addOption(optionNumber, option);
    }


    /**
     * Adds an uint option to this {@link CoapMessage}. However, it is recommended to use the options specific methods
     * from {@link CoapRequest} and {@link CoapResponse} to add options. This method is intended for framework internal
     * use.
     *
     * @param optionNumber the number representing the option type
     * @param value the value of this uint option
     *
     * @throws java.lang.IllegalArgumentException
     */
    protected void addUintOption(int optionNumber, long value) throws IllegalArgumentException {

        if(!(OptionValue.getOptionType(optionNumber) == OptionValue.Type.UINT))
            throw new IllegalArgumentException(String.format(WRONG_OPTION_TYPE, optionNumber, OptionValue.Type.STRING));

        //Add new option to option list
        byte[] byteValue = Longs.toByteArray(value);
        int index = 0;
        while(index < byteValue.length && byteValue[index] == 0)
            index++;

        UintOptionValue option = new UintOptionValue(optionNumber, Arrays.copyOfRange(byteValue, index, byteValue.length));
        addOption(optionNumber, option);

    }


    /**
     * Adds an opaque option to this {@link CoapMessage}. However, it is recommended to use the options specific methods
     * from {@link CoapRequest} and {@link CoapResponse} to add options. This method is intended for framework internal
     * use.
     *
     * @param optionNumber the number representing the option type
     * @param value the value of this opaque option
     *
     * @throws java.lang.IllegalArgumentException
     */
    protected void addOpaqueOption(int optionNumber, byte[] value) throws IllegalArgumentException {

        if(!(OptionValue.getOptionType(optionNumber) == OptionValue.Type.OPAQUE))
            throw new IllegalArgumentException(String.format(WRONG_OPTION_TYPE, optionNumber, OptionValue.Type.OPAQUE));

        //Add new option to option list
        OpaqueOptionValue option = new OpaqueOptionValue(optionNumber, value);
        addOption(optionNumber, option);

    }


    /**
     * Adds an empty option to this {@link CoapMessage}. However, it is recommended to use the options specific methods
     * from {@link CoapRequest} and {@link CoapResponse} to add options. This method is intended for framework internal
     * use.
     *
     * @param optionNumber the number representing the option type
     *
     * @throws java.lang.IllegalArgumentException if the given option number refers to an unknown option or to
     * a not-empty option.
     */
    protected void addEmptyOption(int optionNumber) throws IllegalArgumentException {

        if(!(OptionValue.getOptionType(optionNumber) == OptionValue.Type.EMPTY))
            throw new IllegalArgumentException(String.format(WRONG_OPTION_TYPE, optionNumber, OptionValue.Type.EMPTY));

        //Add new option to option list
        options.put(optionNumber, new EmptyOptionValue(optionNumber));

        log.debug("Added empty option (number: {})", optionNumber);
    }


    /**
     * Removes all options with the given option number from this {@link CoapMessage} instance.
     *
     * @param optionNumber the option number to remove from this message
     *
     * @return the number of options that were removed, i.e. the count.
     */
    public int removeOptions(int optionNumber){
        int result = options.removeAll(optionNumber).size();
        log.debug("Removed {} options with number {}.", result, optionNumber);
        return result;
    }


    private void checkOptionPermission(int optionNumber) throws IllegalArgumentException {
        Integer allowedOccurence = optionOccurenceConstraints.get(this.messageCode, optionNumber);
        if(allowedOccurence == null)
            throw new IllegalArgumentException(String.format(OPTION_NOT_ALLOWED_WITH_MESSAGE_TYPE,
                    optionNumber, this.getMessageCodeName()));

        if(options.containsKey(optionNumber)){
            if(optionOccurenceConstraints.get(this.messageCode, optionNumber) == ONCE)
                throw new IllegalArgumentException(String.format(OPTION_ALREADY_SET, optionNumber));
        }
    }

    private static long extractBits(final long l, final int nrBits, final int offset){
        final long rightShifted = l >>> offset;
        final long mask = (1L << nrBits) - 1L;
        return rightShifted & mask;
    }


    /**
     * Returns the CoAP protocol version used for this {@link CoapMessage}
     *
     * @return the CoAP protocol version used for this {@link CoapMessage}
     */
    public int getProtocolVersion() {
        return PROTOCOL_VERSION;
    }


    /**
     * Sets the message ID for this message. However, there is no need to set the message ID manually. It is set (or
     * overwritten) automatically by the nCoAP framework.
     *
     * @param messageID the message ID for the message
     */
    public void setMessageID(int messageID) throws IllegalArgumentException {

        if(messageID < -1 || messageID > 65535)
            throw new IllegalArgumentException("Message ID " + messageID + " is either negative or greater than 65535");

        this.messageID = messageID;
    }


    /**
     * Returns the message ID (or {@link CoapMessage#UNDEFINED_MESSAGE_ID} if not set)
     *
     * @return the message ID (or {@link CoapMessage#UNDEFINED_MESSAGE_ID} if not set)
     */
    public int getMessageID() {
        return this.messageID;
    }


    /**
     * Returns the number representing the {@link MessageType} of this {@link CoapMessage}
     *
     * @return the number representing the {@link MessageType} of this {@link CoapMessage}
     */
    public int getMessageType() {
        return this.messageType;
    }


    /**
     * Returns the {@link MessageType.Name} of this {@link CoapMessage}. Invocation of
     * {@link MessageType.Name#getNumber()} on the returned value returns the same value as {@link #getMessageType()}.
     *
     * @return the {@link MessageType.Name} of this {@link CoapMessage}
     */
    public MessageType.Name getMessageTypeName(){
        return MessageType.Name.getName(this.messageType);
    }


    /**
     * Returns the number representing the {@link MessageCode} of this {@link CoapMessage}
     *
     * @return the number representing the {@link MessageCode} of this {@link CoapMessage}
     */
    public int getMessageCode() {
        return this.messageCode;
    }


    /**
     * Returns the {@link MessageCode.Name} of this {@link CoapMessage}. Invocation of
     * {@link MessageCode.Name#getNumber()} on the returned value returns the same value as {@link #getMessageCode()}.
     *
     * @return the {@link MessageCode.Name} of this {@link CoapMessage}
     */
    public MessageCode.Name getMessageCodeName(){
        return MessageCode.Name.getName(this.messageCode);
    }


    /**
     * Sets a {@link Token} to this {@link CoapMessage}. However, there is no need to set the {@link Token} manually,
     * as it is set (or overwritten) automatically by the framework.
     *
     * @param token the {@link Token} for this {@link CoapMessage}
     */
    public void setToken(Token token){
        this.token = token;
    }


    /**
     * Returns the {@link Token} of this {@link CoapMessage}
     *
     * @return the {@link Token} of this {@link CoapMessage}
     */
    public Token getToken(){
        return this.token;
    }


    /**
     * Returns the number representing the format of the content or {@link de.uzl.itm.ncoap.message.options.ContentFormat#UNDEFINED} if no such
     * option is present in this {@link CoapMessage}. See {@link de.uzl.itm.ncoap.message.options.ContentFormat} for some constants for predefined
     * numbers (according to the CoAP specification).
     *
     * @return the number representing the format of the content or {@link de.uzl.itm.ncoap.message.options.ContentFormat#UNDEFINED} if no such option
     * is present in this {@link CoapMessage}.
     */
    public long getContentFormat(){
        if(options.containsKey(OptionValue.Name.CONTENT_FORMAT))
            return ((UintOptionValue) options.get(OptionValue.Name.CONTENT_FORMAT).iterator().next()).getDecodedValue();

        return ContentFormat.UNDEFINED;
    }



    /**
     * Sets the observing option in this {@link CoapRequest} and returns
     * <code>true</code> if the option is set after method returns (may already have been set beforehand in a prior
     * method invocation) or <code>false</code if the option is not set, e.g. because that option has no meaning with
     * the message code of this {@link CoapRequest}.
     *
     * @param value <code>true</code> if this {@link CoapRequest} is supposed
     *              to register as an observer and <code>false</code> to deregister as observer, i.e. cancel
     *               an ongoing observation
     */
    public void setObserve(long value){
        try {
            this.removeOptions(OptionValue.Name.OBSERVE);
            value = value & 0xFFFFFF;
            this.addUintOption(OptionValue.Name.OBSERVE, value);
        }
        catch (IllegalArgumentException e){
            this.removeOptions(OptionValue.Name.OBSERVE);
            log.error("This should never happen.", e);
        }
    }


    /**
     * Returns the value of the observing option (no.6) or
     * {@link de.uzl.itm.ncoap.message.options.UintOptionValue#UNDEFINED} if there is no such option present in
     * this {@link CoapRequest}.
     *
     * @return he value of the observing option (no.6) or
     * {@link de.uzl.itm.ncoap.message.options.UintOptionValue#UNDEFINED} if there is no such option present in
     * this {@link CoapRequest}.
     */
    public long getObserve(){
        if(!options.containsKey(OptionValue.Name.OBSERVE))
            return UintOptionValue.UNDEFINED;

        return (long) options.get(OptionValue.Name.OBSERVE).iterator().next().getDecodedValue();
    }



    /**
     * Returns the sequence number of the block2 option or
     * {@link de.uzl.itm.ncoap.message.options.UintOptionValue#UNDEFINED} if there is no such option present in
     * this {@link CoapRequest}.
     *
     * @return the sequence number of the block2 option or
     * {@link de.uzl.itm.ncoap.message.options.UintOptionValue#UNDEFINED} if there is no such option present in
     * this {@link CoapRequest}.
     */
    public long getBlock2Number(){
        if(!options.containsKey(OptionValue.Name.BLOCK2))
            return UintOptionValue.UNDEFINED;

        return (long) options.get(OptionValue.Name.BLOCK2).iterator().next().getDecodedValue() >> 4;
    }


    /**
     * Returns <code>true</code> if
     * <ul>
     *  <li>the BLOCK2 option is present and its value indicates that there are no more blocks to come (should be
     *  always <code>false</code> for {@link CoapRequest}s or
     *  </li>
     *  <li>if there is no BLOCK2 option present.</li>
     * </ul>
     *
     * @return <code>true</code> if there are no more blocks expected.
     */
    public boolean isLastBlock2(){
        if(!options.containsKey(OptionValue.Name.BLOCK2))
            return true;

        long m = (long) options.get(OptionValue.Name.BLOCK2).iterator().next().getDecodedValue();
        return (extractBits(m, 1, 3) == 0);
    }


    /**
     * Returns encoded block size of the block2 option (i.e. the 'szx' portion) or
     * {@link de.uzl.itm.ncoap.message.options.UintOptionValue#UNDEFINED} if there is no such option present in
     * this {@link CoapRequest}.
     *
     * With szx as the returned value the actual blocksize is <code>2^(szx + 4)</code> bytes.
     *
     * @return the block size of the block2 option or
     * {@link de.uzl.itm.ncoap.message.options.UintOptionValue#UNDEFINED} if there is no such option present in
     * this {@link CoapRequest}.
     */
    public long getBlock2EncodedSize(){
        if(!options.containsKey(OptionValue.Name.BLOCK2))
            return UintOptionValue.UNDEFINED;

        long value = (long) options.get(OptionValue.Name.BLOCK2).iterator().next().getDecodedValue();

        // return (block2 & (1 << 3) - 1);
        return extractBits(value, 3, 0);
    }


//    public static int szxToSize(int szx) {
//        return (int)Math.pow(2, 4+szx);
//    }
//
//
//    public int sizeToSzx(int size) {
//        if (size < 16) return 0;
//        if (size > 1024) return 6;
//        return (int)(Math.log(size) / Math.log(2)) - 4;
//    }


    /**
     * Returns the {@link de.uzl.itm.ncoap.communication.identification.EndpointID} contained in this message as
     * {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_1} or <code>null</code> if no such
     * option is present
     *
     * @return the {@link de.uzl.itm.ncoap.communication.identification.EndpointID} contained in this message as
     * {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_1} or <code>null</code> if no such
     * option is present
     */
    public byte[] getEndpointID1(){
        Set<OptionValue> values = getOptions(OptionValue.Name.ENDPOINT_ID_1);
        if(values.isEmpty()){
            return null;
        }

        return values.iterator().next().getValue();
    }

    /**
     * Sets the {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_1} with an empty byte array.
     * This value is replaces with a valid ID by the framework during outbound message processing
     */
    public void setEndpointID1(){
        this.setEndpointID1(new byte[0]);
    }

    /**
     * Sets the {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_1} with the given byte array.
     *
     * <b>Note:</b> This method is intended for internal use. The given value is likely to be replaced by the
     * framework during outbound message processing! Use {@link #setEndpointID1} instead!
     *
     * @param value the {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_1} option value
     */
    public void setEndpointID1(byte[] value){
        try {
            this.removeOptions(OptionValue.Name.ENDPOINT_ID_1);
            this.addOpaqueOption(OptionValue.Name.ENDPOINT_ID_1, value);
        }
        catch (IllegalArgumentException e){
            this.removeOptions(OptionValue.Name.ENDPOINT_ID_1);
            log.error("This should never happen.", e);
        }
    }


    /**
     * Returns the {@link de.uzl.itm.ncoap.communication.identification.EndpointID} contained in this message as
     * {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_2} or <code>null</code> if no such
     * option is present
     *
     * @return the {@link de.uzl.itm.ncoap.communication.identification.EndpointID} contained in this message as
     * {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_2} or <code>null</code> if no such
     * option is present
     */
    public byte[] getEndpointID2(){
        Set<OptionValue> values = getOptions(OptionValue.Name.ENDPOINT_ID_2);
        if(values.isEmpty()){
            return null;
        }

        return values.iterator().next().getValue();
    }


    /**
     * Sets the {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_2} with the given byte array.
     *
     * <b>Note:</b> This method is intended for internal use. The given value is likely to be replaced or removed by the
     * framework during outbound message processing!
     *
     * @param value the {@link de.uzl.itm.ncoap.message.options.OptionValue.Name#ENDPOINT_ID_1} option value
     */
    public void setEndpointID2(byte[] value){
        try {
            this.removeOptions(OptionValue.Name.ENDPOINT_ID_2);
            this.addOpaqueOption(OptionValue.Name.ENDPOINT_ID_2, value);
        }
        catch (IllegalArgumentException e){
            this.removeOptions(OptionValue.Name.ENDPOINT_ID_2);
            log.error("This should never happen.", e);
        }
    }

    /**
     * Adds the content to the message. If this {@link CoapMessage} contained any content prior to the invocation of
     * method, the previous content is removed.
     *
     * @param content ChannelBuffer containing the message content
     *
     * @throws java.lang.IllegalArgumentException if the messages code does not allow content and for the given
     * {@link ChannelBuffer#readableBytes()} is greater then zero.
     */
    public void setContent(ChannelBuffer content) throws IllegalArgumentException {

        if(!(MessageCode.allowsContent(this.messageCode)) && content.readableBytes() > 0)
            throw new IllegalArgumentException(String.format(DOES_NOT_ALLOW_CONTENT, this.getMessageCodeName()));

        this.content = content;
    }


    /**
     * Sets the content (payload) of this {@link CoapMessage}.
     *
     * @param content {@link ChannelBuffer} containing the message content
     * @param contentFormat a long value representing the format of the content (see {@link ContentFormat} for some
     *                      predefined numbers (according to the CoAP specification)
     *
     * @throws java.lang.IllegalArgumentException if the messages code does not allow content and for the given
     * {@link ChannelBuffer#readableBytes()} is greater then zero.
     */
    public void setContent(ChannelBuffer content, long contentFormat) throws IllegalArgumentException {

        try {
            this.addUintOption(OptionValue.Name.CONTENT_FORMAT, contentFormat);
            setContent(content);
        }
        catch (IllegalArgumentException e) {
            this.content = ChannelBuffers.EMPTY_BUFFER;
            this.removeOptions(OptionValue.Name.CONTENT_FORMAT);
            throw e;
        }
    }


    /**
     * Adds the content to the message. If this {@link CoapMessage} contained any content prior to the invocation of
     * method, the previous content is removed.
     *
     * @param content ChannelBuffer containing the message content
     *
     * @throws java.lang.IllegalArgumentException if the messages code does not allow content and the given byte array
     * has a length more than zero.
     */
    public void setContent(byte[] content) throws IllegalArgumentException {

        setContent(ChannelBuffers.wrappedBuffer(content));

    }


    /**
     * Adds the content to the message. If this {@link CoapMessage} contained any content prior to the invocation of
     * method, the previous content is removed.
     *
     * @param content ChannelBuffer containing the message content
     * @param contentFormat a long value representing the format of the content
     *
     * @throws java.lang.IllegalArgumentException if the messages code does not allow content
     */
    public void setContent(byte[] content, long contentFormat) throws IllegalArgumentException {

        setContent(ChannelBuffers.wrappedBuffer(content), contentFormat);

    }


    /**
     * Returns the messages content. If the message does not contain any content, this method returns an empty
     * {@link ChannelBuffer} ({@link ChannelBuffers#EMPTY_BUFFER}).
     *
     * @return Returns the messages content.
     */
    public ChannelBuffer getContent(){
        return content;
    }


    /**
     * Returns a {@link Multimap} with the option numbers as keys and {@link de.uzl.itm.ncoap.message.options.OptionValue}s as values.
     * The returned multimap does not contain options with default values.
     *
     * @return a {@link Multimap} with the option numbers as keys and {@link de.uzl.itm.ncoap.message.options.OptionValue}s as values.
     */
    public SetMultimap<Integer, OptionValue> getAllOptions(){
        return this.options;
    }


    /**
     * Returns a {@link Set<de.uzl.itm.ncoap.message.options.OptionValue>} containing the options that are explicitly set in this {@link CoapMessage}. The
     * returned set does not contain options with default values. If this {@link CoapMessage} does not contain any
     * options of the given option number, then the returned set is empty.
     *
     * @param optionNumber the option number
     *
     * @return a {@link Set<de.uzl.itm.ncoap.message.options.OptionValue>} containing the options that are explicitly set in this {@link CoapMessage}.
     */
    public Set<OptionValue> getOptions(int optionNumber){
        return this.options.get(optionNumber);
    }

    /**
     * Returns <code>true</code> if an option with the given number is contained in this
     * {@link de.uzl.itm.ncoap.message.CoapMessage} and <code>false</code> otherwise.
     *
     * @param optionNumber the option number
     *
     * @return <code>true</code> if an option with the given number is contained in this
     * {@link de.uzl.itm.ncoap.message.CoapMessage} and <code>false</code> otherwise.
     */
    public boolean containsOption(int optionNumber){
        return !getOptions(optionNumber).isEmpty();
    }

    @Override
    public int hashCode(){
        return toString().hashCode() + content.hashCode();
    }

    /**
     * Returns <code>true</code> if and only if the given object is an instance of {@link CoapMessage}
     * and if the header, the token, the options and the content of both instances equal.
     *
     * @param object another object to compare this {@link CoapMessage} with
     *
     * @return <code>true</code> if and only if the given object is an instance of {@link CoapMessage}
     * and if the header, the token, the options and the content of both instances equal.
     */
    @Override
    public boolean equals(Object object){

        if(!(object instanceof CoapMessage)){
            log.error("Different type");
            return false;
        }

        CoapMessage other = (CoapMessage) object;

        //Check header fields
        if(this.getProtocolVersion() != other.getProtocolVersion())
            return false;

        if(this.getMessageType() != other.getMessageType())
            return false;

        if(this.getMessageCode() != other.getMessageCode())
            return false;

        if(this.getMessageID() != other.getMessageID())
            return false;

        if(!this.getToken().equals(other.getToken()))
            return false;


        //Iterators iterate over the contained options
        Iterator<Map.Entry<Integer, OptionValue>> iterator1 = this.getAllOptions().entries().iterator();
        Iterator<Map.Entry<Integer, OptionValue>> iterator2 = other.getAllOptions().entries().iterator();

        //Check if both CoAP Messages contain the same options in the same order
        while(iterator1.hasNext()){

            //Check if iterator2 has no more options while iterator1 has at least one more
            if(!iterator2.hasNext())
                return false;

            Map.Entry<Integer, OptionValue> entry1 = iterator1.next();
            Map.Entry<Integer, OptionValue> entry2 = iterator2.next();

            if(!entry1.getKey().equals(entry2.getKey()))
                return false;

            if(!entry1.getValue().equals(entry2.getValue()))
                return false;
        }

        //Check if iterator2 has at least one more option while iterator1 has no more
        if(iterator2.hasNext())
            return false;

        //Check content
        return this.getContent().equals(other.getContent());
    }


    @Override
    public String toString(){

        StringBuffer result =  new StringBuffer();

        //Header + Token
        result.append("[Header: (V) " + getProtocolVersion() + ", (T) " + getMessageTypeName() + ", (TKL) "
            + token.getBytes().length + ", (C) " + getMessageCodeName() + ", (ID) " + getMessageID() + " | (Token) "
            + token + " | ");

        //Options
        result.append("Options:");
        for(int optionNumber : getAllOptions().keySet()){
            result.append(" (No. " + optionNumber + ") ");
            Iterator<OptionValue> iterator = this.getOptions(optionNumber).iterator();
            OptionValue optionValue = iterator.next();
            result.append(optionValue.toString());
            while(iterator.hasNext())
                result.append(" / " + iterator.next().toString());
        }
        result.append(" | ");

        //Content
        result.append("Content: ");
        long payloadLength = getContent().readableBytes();
        if(payloadLength == 0)
            result.append("<no content>]");
        else
            result.append(getContent().toString(0, Math.min(getContent().readableBytes(), 20), CoapMessage.CHARSET)
                + "... ( " + payloadLength + " bytes)]");

        return result.toString();

    }

    public void setMessageCode(int messageCode) throws IllegalArgumentException {
        if(!MessageCode.Name.isMessageCode(messageCode))
            throw new IllegalArgumentException("Invalid message code no. " + messageCode);

        this.messageCode = messageCode;
    }


    /**
     * This is the supplier to provide the {@link LinkedHashSet} to contain the {@link de.uzl.itm.ncoap.message.options.OptionValue} instances. There
     * is one {@link LinkedHashSet} provided per option number. The order prevention of the values contained
     * in such a set is necessary to keep the order of multiple values for one option (e.g. URI path).
     */
    private final static class LinkedHashSetSupplier implements Supplier<LinkedHashSet<OptionValue>> {

        public static LinkedHashSetSupplier instance = new LinkedHashSetSupplier();

        private LinkedHashSetSupplier(){}

        public static LinkedHashSetSupplier getInstance(){
            return instance;
        }

        @Override
        public LinkedHashSet<OptionValue> get() {
            return new LinkedHashSet<>();
        }
    }
}
