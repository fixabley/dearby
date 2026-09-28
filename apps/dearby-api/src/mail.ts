import nodemailer from 'nodemailer';
export type SendCode = (email: string, code: string) => Promise<void>;
export function smtpMailer(env: NodeJS.ProcessEnv): SendCode {
  const { SMTP_HOST: host, SMTP_USER: user, SMTP_PASS: pass, SMTP_FROM: from } = env;
  const port = Number(env.SMTP_PORT || 465);
  if (!host || !user || !pass || !from || !Number.isInteger(port) || port < 1 || port > 65535) {
    return async () => { throw new Error('Mail unavailable'); };
  }
  const transport = nodemailer.createTransport({ host, port, secure: port === 465, requireTLS: true,
    auth: { user, pass }, logger: false, debug: false, connectionTimeout: 10000, socketTimeout: 15000,
    disableFileAccess: true, disableUrlAccess: true,
  });
  return async (email, code) => {
    const result = await transport.sendMail({ from, to: email, subject: 'Dearby 인증번호', text: `인증번호: ${code}\n5분 안에 입력해 주세요.` });
    if (result.accepted.length !== 1) throw new Error('Mail unavailable');
  };
}
