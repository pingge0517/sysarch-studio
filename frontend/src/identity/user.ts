// The user of this browser (P2 T1, temporary until T8 maps the Polarion login): a name kept in the sysarch_user
// cookie. Cookies are per host, not per port, so Sirius Web on :8080 receives it too; the backend reads it to know
// who edits (element locks). No password, no token.

export const USER_COOKIE = 'sysarch_user';

/** The user in a cookie string (document.cookie), or '' when none. */
export function userFromCookies(cookies: string): string {
  for (const part of cookies.split(';')) {
    const [name, ...value] = part.trim().split('=');
    if (name === USER_COOKIE) {
      try {
        return decodeURIComponent(value.join('=')).trim();
      } catch {
        return '';
      }
    }
  }
  return '';
}

/** The Set-Cookie value for a user; an empty name clears it. */
export function userCookie(user: string): string {
  const name = user.trim().slice(0, 128);
  return name
    ? `${USER_COOKIE}=${encodeURIComponent(name)}; Path=/; Max-Age=31536000; SameSite=Lax`
    : `${USER_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}

export function currentUser(): string {
  return userFromCookies(document.cookie);
}

export function setCurrentUser(user: string): void {
  document.cookie = userCookie(user);
}
