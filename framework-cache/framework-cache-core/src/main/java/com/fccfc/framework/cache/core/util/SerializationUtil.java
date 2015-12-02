/**************************************************************************************** 
 Copyright © 2003-2012 fccfc Corporation. All rights reserved. Reproduction or       <br>
 transmission in whole or in part, in any form or by any means, electronic, mechanical <br>
 or otherwise, is prohibited without the prior written consent of the copyright owner. <br>
 ****************************************************************************************/
package com.fccfc.framework.cache.core.util;

import com.fccfc.framework.common.ErrorCodeDef;
import com.fccfc.framework.common.utils.UtilException;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

/**
 * <Description> <br>
 * 
 * @author 王伟<br>
 * @version 1.0<br>
 * @taskId <br>
 * @CreateDate 2015年12月2日 <br>
 * @since V1.0<br>
 * @see com.fccfc.framework.cache.core.util <br>
 */
public final class SerializationUtil {

    /**
     * INIT_SIZE
     */
    private static final int INIT_SIZE = 4096;

    /**
     * Description: <br>
     * 
     * @author yang.zhipeng <br>
     * @taskId <br>
     * @param obj <br>
     * @return <br>
     * @throws UtilException UtilException
     */
    @SuppressWarnings("unchecked")
    public static <T> byte[] serial(T obj) throws UtilException {
        byte[] result = null;
        if (obj != null && !(obj instanceof Void)) {
            Schema<T> schema = RuntimeSchema.getSchema((Class<T>) obj.getClass());
            LinkedBuffer buffer = LinkedBuffer.allocate(INIT_SIZE);
            try {
                result = ProtostuffIOUtil.toByteArray(obj, schema, buffer);
            }
            catch (Exception e) {
                throw new UtilException(ErrorCodeDef.SERIALIZE_ERROR, e);
            }
        }
        return result;
    }

    /**
     * Description: <br>
     * 
     * @author yang.zhipeng <br>
     * @taskId <br>
     * @param data <br>
     * @return <br>
     * @throws UtilException UtilException
     */
    public static <T> T unserial(Class<T> clazz, byte[] data) throws UtilException {
        T result = null;
        if (data != null && data.length > 0) {
            Schema<T> schema = RuntimeSchema.getSchema(clazz);
            try {
                result = clazz.newInstance();
                ProtostuffIOUtil.mergeFrom(data, result, schema);
            }
            catch (Exception e) {
                throw new UtilException(ErrorCodeDef.UNSERIALIZE_ERROR, e);
            }
        }
        return result;
    }
}
