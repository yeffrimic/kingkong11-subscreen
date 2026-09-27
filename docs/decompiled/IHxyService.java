package com.mediatek.hxymanager;

/* JADX INFO: loaded from: C:\Users\<USUARIO>\diag-kingkong11\dex_hxy\..\fw\dex\classes5.dex */
public interface IHxyService extends android.os.IInterface {
    public static final java.lang.String DESCRIPTOR = "com.mediatek.hxymanager.IHxyService";

    java.lang.String getServiceInfo(java.lang.String str) throws android.os.RemoteException;

    boolean handleIntent(android.content.Intent intent) throws android.os.RemoteException;

    boolean handleKeycode(int i, int i2) throws android.os.RemoteException;

    boolean updateServiceInfo(java.lang.String str, java.lang.String str2) throws android.os.RemoteException;

    public static class Default implements com.mediatek.hxymanager.IHxyService {
        @Override // com.mediatek.hxymanager.IHxyService
        public boolean handleIntent(android.content.Intent intent) throws android.os.RemoteException {
            return false;
        }

        @Override // com.mediatek.hxymanager.IHxyService
        public boolean updateServiceInfo(java.lang.String key, java.lang.String value) throws android.os.RemoteException {
            return false;
        }

        @Override // com.mediatek.hxymanager.IHxyService
        public java.lang.String getServiceInfo(java.lang.String key) throws android.os.RemoteException {
            return null;
        }

        @Override // com.mediatek.hxymanager.IHxyService
        public boolean handleKeycode(int keyCode, int action) throws android.os.RemoteException {
            return false;
        }

        @Override // android.os.IInterface
        public android.os.IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends android.os.Binder implements com.mediatek.hxymanager.IHxyService {
        static final int TRANSACTION_getServiceInfo = 3;
        static final int TRANSACTION_handleIntent = 1;
        static final int TRANSACTION_handleKeycode = 4;
        static final int TRANSACTION_updateServiceInfo = 2;

        public Stub() {
            attachInterface(this, com.mediatek.hxymanager.IHxyService.DESCRIPTOR);
        }

        public static com.mediatek.hxymanager.IHxyService asInterface(android.os.IBinder obj) {
            if (obj == null) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(com.mediatek.hxymanager.IHxyService.DESCRIPTOR);
            if (iin != null && (iin instanceof com.mediatek.hxymanager.IHxyService)) {
                return (com.mediatek.hxymanager.IHxyService) iin;
            }
            return new com.mediatek.hxymanager.IHxyService.Stub.Proxy(obj);
        }

        @Override // android.os.IInterface
        public android.os.IBinder asBinder() {
            return this;
        }

        public static java.lang.String getDefaultTransactionName(int transactionCode) {
            switch (transactionCode) {
                case 1:
                    return "handleIntent";
                case 2:
                    return "updateServiceInfo";
                case 3:
                    return "getServiceInfo";
                case 4:
                    return "handleKeycode";
                default:
                    return null;
            }
        }

        public java.lang.String getTransactionName(int transactionCode) {
            return getDefaultTransactionName(transactionCode);
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            if (code >= 1 && code <= 16777215) {
                data.enforceInterface(com.mediatek.hxymanager.IHxyService.DESCRIPTOR);
            }
            if (code == 1598968902) {
                reply.writeString(com.mediatek.hxymanager.IHxyService.DESCRIPTOR);
                return true;
            }
            switch (code) {
                case 1:
                    android.content.Intent _arg0 = (android.content.Intent) data.readTypedObject(android.content.Intent.CREATOR);
                    data.enforceNoDataAvail();
                    boolean _result = handleIntent(_arg0);
                    reply.writeNoException();
                    reply.writeBoolean(_result);
                    return true;
                case 2:
                    java.lang.String _arg1 = data.readString();
                    java.lang.String _arg2 = data.readString();
                    data.enforceNoDataAvail();
                    boolean _result2 = updateServiceInfo(_arg1, _arg2);
                    reply.writeNoException();
                    reply.writeBoolean(_result2);
                    return true;
                case 3:
                    java.lang.String _arg3 = data.readString();
                    data.enforceNoDataAvail();
                    java.lang.String _result3 = getServiceInfo(_arg3);
                    reply.writeNoException();
                    reply.writeString(_result3);
                    return true;
                case 4:
                    int _arg4 = data.readInt();
                    int _arg5 = data.readInt();
                    data.enforceNoDataAvail();
                    boolean _result4 = handleKeycode(_arg4, _arg5);
                    reply.writeNoException();
                    reply.writeBoolean(_result4);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements com.mediatek.hxymanager.IHxyService {
            private android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public android.os.IBinder asBinder() {
                return this.mRemote;
            }

            public java.lang.String getInterfaceDescriptor() {
                return com.mediatek.hxymanager.IHxyService.DESCRIPTOR;
            }

            @Override // com.mediatek.hxymanager.IHxyService
            public boolean handleIntent(android.content.Intent intent) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(com.mediatek.hxymanager.IHxyService.DESCRIPTOR);
                    _data.writeTypedObject(intent, 0);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.mediatek.hxymanager.IHxyService
            public boolean updateServiceInfo(java.lang.String key, java.lang.String value) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(com.mediatek.hxymanager.IHxyService.DESCRIPTOR);
                    _data.writeString(key);
                    _data.writeString(value);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.mediatek.hxymanager.IHxyService
            public java.lang.String getServiceInfo(java.lang.String key) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(com.mediatek.hxymanager.IHxyService.DESCRIPTOR);
                    _data.writeString(key);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                    java.lang.String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.mediatek.hxymanager.IHxyService
            public boolean handleKeycode(int keyCode, int action) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(com.mediatek.hxymanager.IHxyService.DESCRIPTOR);
                    _data.writeInt(keyCode);
                    _data.writeInt(action);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public int getMaxTransactionId() {
            return 3;
        }
    }
}
