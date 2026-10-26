"use client";
import { useRef, useState } from "react";
import { errorMessage, RequestError } from "@/lib/client";
import { Button } from "@/shared/ui/button";
import { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { Icon } from "@/shared/ui/icon";
import { deletePhoto, putPhoto } from "../api/profile-client";
import { PhotoCrop } from "./photo-crop";

/**
 * 사진 칸 at the top of edit mode (contract "프로필 사진", "프로필 읽기/수정 모드" › 수정 모드 맨 위 `사진` 칸): the current photo or the
 * initial, `사진 바꾸기` (file → crop → `PUT /v1/profile/photo`) and `사진 지우기` (confirm with the note that issued cards keep theirs).
 * Saved on its own, apart from the profile version.
 */
export function PhotoField({ src, name, onChanged, onNotice }: { src: string | undefined; name: string; onChanged: (photoUrl: string | null) => void; onNotice: (text: string) => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [picked, setPicked] = useState<File>();
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  async function upload(file: File) {
    setPicked(undefined);
    setBusy(true);
    try {
      const { photoUrl } = await putPhoto(file);
      onChanged(photoUrl);
      onNotice("사진을 바꿨어요.");
    } catch (error) {
      onNotice(error instanceof RequestError && error.detail ? error.detail : errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  async function remove() {
    setConfirming(false);
    setBusy(true);
    try {
      await deletePhoto();
      onChanged(null);
      onNotice("사진을 지웠어요.");
    } catch (error) {
      onNotice(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="flex items-center gap-4">
      {src ? (
        // Same-origin proxy URL with the photo's `v` (contract "프로필 사진"); next/image has nothing to optimise.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt="프로필 사진" className="size-20 shrink-0 rounded-full object-cover" />
      ) : (
        <span aria-hidden="true" className="grid size-20 shrink-0 place-items-center rounded-full bg-mint text-title1 font-bold text-teal">
          {Array.from(name.trim())[0] || <Icon name="person" />}
        </span>
      )}
      <div className="flex flex-col gap-1">
        <input
          ref={input}
          type="file"
          accept="image/*"
          className="sr-only"
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = "";
            if (file) setPicked(file);
          }}
        />
        <Button variant="tinted" size="sm" disabled={busy} onClick={() => input.current?.click()}>
          {busy ? "올리는 중…" : src ? "사진 바꾸기" : "사진 넣기"}
        </Button>
        {src && (
          <Button variant="destructive" size="sm" disabled={busy} onClick={() => setConfirming(true)}>
            사진 지우기
          </Button>
        )}
        <p className="text-caption text-secondary-label">정사각형으로 잘라 올리고, 화면에는 원형으로 보여요. 선택 항목이에요.</p>
      </div>
      <PhotoCrop file={picked} onDone={upload} onCancel={() => setPicked(undefined)} />
      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent>
          <DialogTitle>사진을 지울까요?</DialogTitle>
          <DialogDescription>이미 만든 명함의 사진은 명함을 고치거나 지워야 바뀌어요.</DialogDescription>
          <DialogActions>
            <DialogClose className="text-body text-teal">취소</DialogClose>
            <button type="button" className="text-body font-semibold text-danger" onClick={remove}>
              지우기
            </button>
          </DialogActions>
        </DialogContent>
      </Dialog>
    </div>
  );
}
