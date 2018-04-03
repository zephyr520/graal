/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;

abstract class ToFloat extends ForeignToLLVM {

    @Child private ToFloat toFloat;

    @Specialization
    protected float fromInt(int value) {
        return value;
    }

    @Specialization
    protected float fromLong(long value) {
        return value;
    }

    @Specialization
    protected float fromChar(char value) {
        return value;
    }

    @Specialization
    protected float fromShort(short value) {
        return value;
    }

    @Specialization
    protected float fromByte(byte value) {
        return value;
    }

    @Specialization
    protected float fromFloat(float value) {
        return value;
    }

    @Specialization
    protected float fromDouble(double value) {
        return (float) value;
    }

    @Specialization
    protected float fromBoolean(boolean value) {
        return (value ? 1.0f : 0.0f);
    }

    @Specialization
    protected float fromString(String value) {
        return getSingleStringCharacter(value);
    }

    @Specialization
    protected float fromLLVMFunctionDescriptor(LLVMFunctionDescriptor fd,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        return toNative.executeWithTarget(fd).getVal();
    }

    @Specialization
    protected float fromLLVMTruffleAddress(LLVMTruffleAddress obj) {
        return obj.getAddress().getVal();
    }

    @Specialization
    protected float fromSharedDescriptor(LLVMSharedGlobalVariable shared,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode access) {
        return access.executeWithTarget(shared.getDescriptor()).getVal();
    }

    @Specialization
    protected float fromForeignPrimitive(LLVMBoxedPrimitive boxed) {
        return recursiveConvert(boxed.getValue());
    }

    @Specialization(guards = "notLLVM(obj)")
    protected float fromTruffleObject(TruffleObject obj) {
        return recursiveConvert(fromForeign(obj));
    }

    private float recursiveConvert(Object o) {
        if (toFloat == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toFloat = ToFloatNodeGen.create();
        }
        return (float) toFloat.executeWithTarget(o);
    }

    @TruffleBoundary
    static float slowPathPrimitiveConvert(LLVMMemory memory, ForeignToLLVM thiz, Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        } else if (value instanceof Boolean) {
            return (boolean) value ? 1.0f : 0.0f;
        } else if (value instanceof Character) {
            return (char) value;
        } else if (value instanceof String) {
            return thiz.getSingleStringCharacter((String) value);
        } else if (value instanceof LLVMFunctionDescriptor) {
            return ((LLVMFunctionDescriptor) value).toNative().asPointer();
        } else if (value instanceof LLVMBoxedPrimitive) {
            return slowPathPrimitiveConvert(memory, thiz, ((LLVMBoxedPrimitive) value).getValue());
        } else if (value instanceof LLVMTruffleAddress) {
            return ((LLVMTruffleAddress) value).getAddress().getVal();
        } else if (value instanceof LLVMSharedGlobalVariable) {
            LLVMContext context = LLVMLanguage.getLLVMContextReference().get();
            return LLVMGlobal.toNative(context, memory, ((LLVMSharedGlobalVariable) value).getDescriptor()).getVal();
        } else if (value instanceof TruffleObject && notLLVM((TruffleObject) value)) {
            return slowPathPrimitiveConvert(memory, thiz, thiz.fromForeign((TruffleObject) value));
        } else {
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }
}
