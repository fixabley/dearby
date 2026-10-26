"use client";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/shared/ui/button";
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetTitle,
} from "@/shared/ui/sheet";

const FRAME = 280;
const OUTPUT = 1024;

/** Decodes the chosen file with EXIF orientation applied (`createImageBitmap`), falling back to an `<img>`; undefined when the browser cannot read it (HEIC). */
async function decode(
  file: File,
): Promise<ImageBitmap | HTMLImageElement | undefined> {
  try {
    return await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    return new Promise((resolve) => {
      const url = URL.createObjectURL(file);
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => {
        URL.revokeObjectURL(url);
        resolve(undefined);
      };
      image.src = url;
    });
  }
}

/**
 * 프로필 사진 자르기 (contract "프로필 사진" › 앱이 올리는 것): the person drags the picture inside a square frame and zooms with a
 * slider; the square is exported as a JPEG of at most 1024px, orientation already applied. A file the browser cannot decode (HEIC)
 * is refused with a hint to use JPEG or PNG.
 */
export function PhotoCrop({
  file,
  onDone,
  onCancel,
}: {
  file: File | undefined;
  onDone: (cropped: File) => void;
  onCancel: () => void;
}) {
  return (
    <Sheet
      open={file !== undefined}
      onOpenChange={(open) => !open && onCancel()}
    >
      <SheetContent>
        <div className="grid grid-cols-[1fr_auto_1fr] items-center">
          <span />
          <SheetTitle className="text-center">사진 자르기</SheetTitle>
          <SheetClose className="-mr-2 inline-flex min-h-11 items-center justify-self-end px-2 text-body text-teal">
            닫기
          </SheetClose>
        </div>
        <SheetDescription className="mt-1 text-center">
          끌어서 위치를, 슬라이더로 크기를 맞춰 주세요. 화면에는 원형으로
          보여요.
        </SheetDescription>
        {/* Keyed by the file so a new pick starts from a fresh frame. */}
        {file && (
          <Cropper
            key={`${file.name}-${file.size}-${file.lastModified}`}
            file={file}
            onDone={onDone}
          />
        )}
      </SheetContent>
    </Sheet>
  );
}

function Cropper({
  file,
  onDone,
}: {
  file: File;
  onDone: (cropped: File) => void;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [source, setSource] = useState<ImageBitmap | HTMLImageElement>();
  const [failed, setFailed] = useState(false);
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const drag = useRef<{ x: number; y: number; ox: number; oy: number }>(null);
  useEffect(() => {
    let cancelled = false;
    decode(file).then((image) => {
      if (cancelled) return;
      if (image) setSource(image);
      else setFailed(true);
    });
    return () => {
      cancelled = true;
    };
  }, [file]);
  const width = source?.width ?? 0;
  const height = source?.height ?? 0;
  /** Scale that makes the shorter side fill the frame, times the zoom. */
  const base = source ? FRAME / Math.min(width, height) : 1;
  const scale = base * zoom;
  const clamp = (value: { x: number; y: number }) => {
    const maxX = Math.max(0, (width * scale - FRAME) / 2);
    const maxY = Math.max(0, (height * scale - FRAME) / 2);
    return {
      x: Math.min(maxX, Math.max(-maxX, value.x)),
      y: Math.min(maxY, Math.max(-maxY, value.y)),
    };
  };
  useEffect(() => {
    const element = canvas.current;
    if (!element || !source) return;
    const context = element.getContext("2d");
    if (!context) return;
    context.clearRect(0, 0, FRAME, FRAME);
    const drawn = clamp(offset);
    context.drawImage(
      source,
      FRAME / 2 - (width * scale) / 2 + drawn.x,
      FRAME / 2 - (height * scale) / 2 + drawn.y,
      width * scale,
      height * scale,
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `clamp` derives from the same state as the listed deps.
  }, [source, scale, offset, width, height]);

  function done() {
    if (!source) return;
    const size = Math.min(OUTPUT, Math.round(FRAME / scale));
    const output = document.createElement("canvas");
    output.width = size;
    output.height = size;
    const context = output.getContext("2d");
    if (!context) return;
    const drawn = clamp(offset);
    // Source rectangle of the frame: frame centre minus half the image, in source pixels.
    const sx = (width * scale) / 2 - FRAME / 2 - drawn.x;
    const sy = (height * scale) / 2 - FRAME / 2 - drawn.y;
    context.fillStyle = "#fff";
    context.fillRect(0, 0, size, size);
    context.drawImage(
      source,
      sx / scale,
      sy / scale,
      FRAME / scale,
      FRAME / scale,
      0,
      0,
      size,
      size,
    );
    output.toBlob(
      (blob) => {
        if (blob) onDone(new File([blob], "photo.jpg", { type: "image/jpeg" }));
      },
      "image/jpeg",
      0.9,
    );
  }
  return (
    <>
      {failed ? (
        <p role="alert" className="py-10 text-center text-body text-danger">
          이 형식은 열 수 없어요. JPEG나 PNG로 올려 주세요
        </p>
      ) : (
        <div className="mt-4 flex flex-col items-center gap-4">
          <div
            className="relative touch-none"
            style={{ width: FRAME, height: FRAME }}
          >
            <canvas
              ref={canvas}
              width={FRAME}
              height={FRAME}
              className="rounded-control bg-fill"
              aria-label="자를 사진"
              onPointerDown={(event) => {
                drag.current = {
                  x: event.clientX,
                  y: event.clientY,
                  ox: offset.x,
                  oy: offset.y,
                };
                event.currentTarget.setPointerCapture(event.pointerId);
              }}
              onPointerMove={(event) => {
                if (!drag.current) return;
                setOffset(
                  clamp({
                    x: drag.current.ox + event.clientX - drag.current.x,
                    y: drag.current.oy + event.clientY - drag.current.y,
                  }),
                );
              }}
              onPointerUp={() => (drag.current = null)}
              onPointerCancel={() => (drag.current = null)}
            />
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 rounded-full border-2 border-white/80 shadow-[0_0_0_9999px_rgb(0_0_0/0.35)]"
            />
          </div>
          <label className="flex w-full max-w-[320px] items-center gap-3 text-footnote text-secondary-label">
            크기
            <input
              type="range"
              min={1}
              max={3}
              step={0.01}
              value={zoom}
              className="flex-1 accent-teal-fill"
              onChange={(event) => setZoom(Number(event.target.value))}
            />
          </label>
          <Button size="lg" disabled={!source} onClick={done}>
            이 사진으로
          </Button>
        </div>
      )}
    </>
  );
}
