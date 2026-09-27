package com.example.subscreenpoke;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Cliente binder crudo del servicio propietario de la pantalla trasera.
 *
 * NO reusa com.mediatek.hxymanager.IHxyService: esa clase existe en
 * /system/framework/framework.jar, gana sobre cualquier copia de la app, y su
 * asInterface() es hidden-API bloqueada para apps (NoSuchMethodError).
 *
 * En vez de eso hablamos el protocolo AIDL a mano con IBinder.transact(), que es
 * API publica. El descriptor y los transaction codes se sacaron del propio
 * framework del telefono (DIAGNOSTICO.md §8.bis):
 *
 *   descriptor = "com.mediatek.hxymanager.IHxyService"
 *   1 = handleIntent   2 = updateServiceInfo   3 = getServiceInfo   4 = handleKeycode
 */
public class HxyClient {

    public static final String DESCRIPTOR = "com.mediatek.hxymanager.IHxyService";
    private static final int TX_UPDATE_INFO = 2;
    private static final int TX_GET_INFO = 3;
    private static final int TX_HANDLE_KEYCODE = 4;

    private final IBinder mRemote;

    public HxyClient(IBinder remote) {
        mRemote = remote;
    }

    public String remoteDescriptor() {
        try {
            return mRemote.getInterfaceDescriptor();
        } catch (RemoteException e) {
            return "?";
        }
    }

    public boolean handleKeycode(int keyCode, int action) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(keyCode);
            data.writeInt(action);
            mRemote.transact(TX_HANDLE_KEYCODE, data, reply, 0);
            reply.readException();
            return reply.readInt() != 0;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public String getServiceInfo(String key) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(key);
            mRemote.transact(TX_GET_INFO, data, reply, 0);
            reply.readException();
            return reply.readString();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public boolean updateServiceInfo(String key, String value) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(key);
            data.writeString(value);
            mRemote.transact(TX_UPDATE_INFO, data, reply, 0);
            reply.readException();
            return reply.readInt() != 0;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
