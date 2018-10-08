package com.speedycloud.spobjsdk.http;

import android.util.Log;

import com.speedycloud.spobjsdk.utils.AsyncRun;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

/**
 * Created by gyb on 2018/4/3.
 */

public class CountingRequestBody extends RequestBody {
    private static final int SEGMENT_SIZE = 2048 * 200; // okio.Segment.SIZE

    private final RequestBody body;
    private final ProgressHandler progress;
    private final long totalSize;
    private final CancellationHandler cancellationHandler;
    private final long sendBlockSize = SEGMENT_SIZE;

    public CountingRequestBody(RequestBody body, ProgressHandler progress, long totalSize,
                               CancellationHandler cancellationHandler) {
        this.body = body;
        this.progress = progress;
        this.totalSize = totalSize;
        this.cancellationHandler = cancellationHandler;
    }

    @Override
    public long contentLength() throws IOException {
        return body.contentLength();
    }

    @Override
    public MediaType contentType() {
        return body.contentType();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        BufferedSink bufferedSink;

        CountingSink countingSink = new CountingSink(sink);
        bufferedSink = Okio.buffer(countingSink);

        body.writeTo(bufferedSink);

        bufferedSink.flush();
    }

    protected final class CountingSink extends ForwardingSink {

        private int bytesWritten = 0;

        public CountingSink(Sink delegate) {
            super(delegate);
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            long iCount = (byteCount + sendBlockSize -1) / sendBlockSize;
            Log.d("gyb","icount = " + iCount + " byteCount = " + byteCount + " sendBlockSize= " + sendBlockSize);
            while (iCount > 0) {
                long sendByteCount = sendBlockSize;
                if(iCount == 1){
                    sendByteCount = byteCount % sendBlockSize;
                    Log.d("gyb","icount = 1 sendByteCount = " + sendByteCount);
                }
                if (cancellationHandler == null && progress == null) {
                    super.write(source, sendByteCount);
                    Log.d("gyb","cancellationHandler = null ");
                    return;
                }
                if (cancellationHandler != null && cancellationHandler.isCancelled()) {
                    Log.d("gyb","throw new CancellationHandler.CancellationException ");
                    throw new CancellationHandler.CancellationException();
                }
                super.write(source, sendByteCount);
                bytesWritten += sendByteCount;
                Log.d("gyb","iCount = " + iCount);
                if (progress != null) {
                    AsyncRun.runInMain(new Runnable() {
                        @Override
                        public void run() {
                            progress.onProgress(bytesWritten, totalSize);
                        }
                    });
                }
                iCount--;
            }
        }
    }
}
