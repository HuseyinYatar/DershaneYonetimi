import { Bell } from "lucide-react";
import { Link } from "react-router";
import { useUnreadCount } from "@/api/queries/notification";
import { useMe } from "@/api/queries/user";
import { useActor } from "@/auth/useActor";
import { initialsOf } from "@/lib/format";

// Round icon buttons top right: bell (violet dot when there is something unread) and avatar.
// Only students are notified, so only they get the bell.
export function HeaderActions() {
  const showBell = !!useActor()?.roles.includes("student");
  const unread = useUnreadCount(showBell).data ?? 0;
  const me = useMe().data;
  const fullName = [me?.firstName, me?.lastName].filter(Boolean).join(" ");

  return (
    <div className="flex items-center gap-2">
      {showBell && (
        <Link
          to="/notifications"
          aria-label={unread > 0 ? `Bildirimler, ${unread} okunmamış` : "Bildirimler"}
          className="relative inline-flex size-10 items-center justify-center rounded-full border border-line bg-white"
        >
          <Bell size={16} strokeWidth={1.5} aria-hidden="true" />
          {unread > 0 && (
            <span
              aria-hidden="true"
              className="absolute top-2 right-[9px] box-content size-[7px] rounded-full border-2 border-white bg-brand"
            />
          )}
        </Link>
      )}
      <span
        role="img"
        aria-label={fullName ? `Profil: ${fullName}` : "Profil"}
        className="inline-flex size-10 items-center justify-center rounded-full bg-ink text-[13px] font-medium tracking-[0.02em] text-white"
      >
        {initialsOf(me?.firstName, me?.lastName)}
      </span>
    </div>
  );
}
