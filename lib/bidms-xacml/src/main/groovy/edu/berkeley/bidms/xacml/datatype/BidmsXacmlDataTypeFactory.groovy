/*
 * Copyright (c) 2023, Regents of the University of California and
 * contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.berkeley.bidms.xacml.datatype

import com.att.research.xacml.api.DataType
import com.att.research.xacml.api.Identifier
import com.att.research.xacml.std.StdDataTypeFactory

class BidmsXacmlDataTypeFactory extends StdDataTypeFactory {

    public static final DT_BIDMS_MAP = BidmsXacmlDataTypeMap.newInstance()
    public static final DT_BIDMS_PERSON = BidmsXacmlDataTypePerson.newInstance()

    BidmsXacmlDataTypeFactory() {
        super()
    }

    @Override
    DataType<?> getDataType(Identifier dataTypeId) {
        if (dataTypeId == BidmsXacmlDataTypeMap.ID_DATATYPE_BIDMS_MAP) {
            return DT_BIDMS_MAP
        } else if (dataTypeId == BidmsXacmlDataTypePerson.ID_DATATYPE_BIDMS_PERSON) {
            return DT_BIDMS_PERSON
        }
        return super.getDataType(dataTypeId)
    }
}
