/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.azure.functions;

import io.ballerina.runtime.api.Future;
import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import org.ballerinalang.langlib.array.ToBase64;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.ballerina.runtime.api.utils.StringUtils.fromString;

/**
 * {@code FunctionCallback} used to handle the Azure function service method invocation results.
 */
public class FunctionCallback implements Callback {


    private final Future future;
    private final Module module;
    private final List<String> annotations;


    public FunctionCallback(Future future, Module module, Object[] annotations) {
        this.future = future;
        this.module = module;
        this.annotations = new ArrayList<>();
        for (Object o : annotations) {
            BString annotation = (BString) o;
            String[] split = annotation.getValue().split(":");
            this.annotations.add(split[split.length - 1]);
        }
    }

    @Override
    public void notifySuccess(Object result) {
        if (result instanceof BError) {
            BError error = (BError) result;
            if (!isModuleDefinedError(error)) {
                error.printStackTrace();
            }
            future.complete(result);
            return;
        }
        BMap<BString, Object> mapValue =
                ValueCreator.createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA));
        String outputBinding = this.annotations.get(0);
        if (Constants.QUEUE_OUTPUT.equals(outputBinding) ||
                Constants.COSMOS_DBOUTPUT.equals(outputBinding)) {

            mapValue.put(StringUtils.fromString(Constants.OUT_MSG), result);
            // Check HTTPOutput with annotations
        } else if (Constants.BLOB_OUTPUT.equals(outputBinding)) {
            if (result instanceof BArray) {
                BArray arrayValue = (BArray) result;
                BString encodedString = ToBase64.toBase64(arrayValue);
                mapValue.put(StringUtils.fromString("outMsg"), encodedString);
            }
        }else if (Constants.HTTP_OUTPUT.equals(outputBinding)) {
            //Check HTTPResponse
            if (isHTTPResponse(result)) {
                BMap resultMap = (BMap) result;

                // Extract status code
                BObject status = (BObject) (resultMap.get(StringUtils.fromString(Constants.STATUS)));
                Object statusCode = Long.toString(status.getIntValue(StringUtils.fromString(Constants.CODE)));
                statusCode = StringUtils.fromString((String) statusCode);

                // Create a BMap for response field
                BMap<BString, Object> respMap =
                        ValueCreator.createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA));
                respMap.put(StringUtils.fromString(Constants.STATUS_CODE), statusCode);

                // Create body field in the response Map
                if (resultMap.containsKey(StringUtils.fromString(Constants.BODY))) {
                    Object body = resultMap.get(StringUtils.fromString(Constants.BODY));
                    respMap.put(StringUtils.fromString(Constants.BODY), body);
                }

                // Create header field in the response Map
                if (resultMap.containsKey(StringUtils.fromString(Constants.HEADERS))) {
                    Object headers = resultMap.get(StringUtils.fromString(Constants.HEADERS));
                    BMap headersMap = (BMap) headers;
                    // Add Content-type field in headers if there is not
                    if (!isContentTypeExist(headersMap)) {
                        headersMap.put(StringUtils.fromString(Constants.CONTENT_TYPE),
                                StringUtils.fromString("text/plain"));
                    }
                    respMap.put(StringUtils.fromString(Constants.HEADERS), headers);
                } else {
                    // If there is no headers add one with default content-type
                    Object headers =
                            ValueCreator.createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA));
                    ((BMap) headers).put(StringUtils.fromString(Constants.CONTENT_TYPE),
                            StringUtils.fromString("text/plain"));
                    respMap.put(StringUtils.fromString(Constants.HEADERS), headers);

                }

                // If there is mediaType replace content-type in headers
                if (resultMap.containsKey(StringUtils.fromString(Constants.MEDIA_TYPE))) {
                    Object headers = resultMap.get(StringUtils.fromString(Constants.HEADERS));
                    Object mediaType = resultMap.get(StringUtils.fromString(Constants.MEDIA_TYPE));
                    ((BMap) headers).put(StringUtils.fromString(Constants.CONTENT_TYPE), mediaType);
                }
                mapValue.put(StringUtils.fromString(Constants.RESP), respMap);

            } else {
                //Handle result except HTTPResponse cases
                BMap<BString, Object> respMap =
                        ValueCreator.createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA));
                respMap.put(StringUtils.fromString(Constants.BODY), result);
                mapValue.put(StringUtils.fromString(Constants.RESP), respMap);
            }
        } else {
            // Handle other output bindings
            BMap<BString, Object> respMap =
                    ValueCreator.createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA));
            respMap.put(StringUtils.fromString(Constants.BODY), result);
            mapValue.put(StringUtils.fromString(Constants.RESP), respMap);
        }
        future.complete(mapValue);
    }

    @Override
    public void notifyFailure(BError bError) {
        bError.printStackTrace();
        BString errorMessage = fromString("service method invocation failed: " + bError.getErrorMessage());
        BError invocationError = ErrorCreator.createError(module, "ServiceExecutionError",
                errorMessage, bError, null);
        future.complete(invocationError);
    }

    private boolean isModuleDefinedError(BError error) {
        Type errorType = error.getType();
        Module packageDetails = errorType.getPackage();
        String orgName = packageDetails.getOrg();
        String packageName = packageDetails.getName();
        return Constants.PACKAGE_ORG.equals(orgName) && Constants.PACKAGE_NAME.equals(packageName);
    }

    private boolean isHTTPResponse(Object result) {
        Module resultPkg = TypeUtils.getType(result).getPackage();
        return (result instanceof  BMap) && (((BMap) result).containsKey(fromString(Constants.STATUS))) &&
                Constants.PACKAGE_ORG.equals(resultPkg.getOrg()) &&
                Constants.PACKAGE_NAME.equals(resultPkg.getName());
        //TODO : Check inheritance
        //(https://github.com/ballerina-platform/module-ballerinax-azure.functions/issues/490)
    }

    private boolean isContentTypeExist(BMap<BString , ?> headersMap) {
        for (BString headerKey : headersMap.getKeys()) {
            if (headerKey.getValue().toLowerCase(Locale.ROOT).equals(Constants.CONTENT_TYPE)) {
                return  true;
            }
        }
        return false;
    }
}
