package com.irvinsegura.wallet.notifications.domain.model;

import java.util.Map;
import java.util.StringJoiner;

/**
 * Turns an {@link EmailDraft} into the email's two forms. The HTML is designed as a bank receipt: when money moved,
 * its amount is the largest thing in the email, and the figures that prove it (date, folio, account) are set like the
 * lines of a voucher, between perforated rules.
 * <p>
 * It follows what mail clients actually support: nested tables, inline styles and system fonts. Nothing depends on an
 * image, because most clients block them by default; icons are typographic marks, which unlike emoji look the same
 * everywhere. Light only, because clients that invert colors on their own would undo a dark version anyway.
 * <p>
 * Every value is escaped on its way in, so a name such as {@code <b>Ana</b>} shows up as written and cannot add markup
 * or links to an email sent from our address.
 */
final class EmailLayout {

    private record Colors(String accent, String soft) {
    }

    private static final Map<EmailDraft.Tone, Colors> TONES = Map.of(
            EmailDraft.Tone.SUCCESS, new Colors("#047857", "#d1fae5"),
            EmailDraft.Tone.INFO, new Colors("#4338ca", "#e0e7ff"),
            EmailDraft.Tone.WARNING, new Colors("#b45309", "#fef3c7"),
            EmailDraft.Tone.DANGER, new Colors("#b91c1c", "#fee2e2"));

    private static final String FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    private static final String MONO = "'SFMono-Regular',Menlo,Consolas,'Liberation Mono',monospace";
    // Figures of the same width line up in columns, as on a statement
    private static final String FIGURES = "font-variant-numeric:tabular-nums;";
    private static final String BRAND = "#4338ca";
    private static final String NAVY = "#1e1b4b";
    private static final String TEXT = "#0f172a";
    private static final String BODY = "#475569";
    private static final String MUTED = "#64748b";
    private static final String LINE = "#e2e8f0";
    private static final String PERFORATION = "#cbd5e1";
    private static final String PAGE = "#eef1f6";
    private static final String PANEL = "#f8fafc";

    private EmailLayout() {
    }

    static EmailMessage message(String to, EmailDraft draft) {
        return new EmailMessage(to, draft.subject, text(draft), html(draft));
    }

    static String text(EmailDraft draft) {
        StringJoiner blocks = new StringJoiner("\n\n", "", "\n");
        if (draft.amount != null) {
            blocks.add(draft.amountLabel + ": " + draft.amount);
        }
        blocks.add(draft.greeting);
        draft.paragraphs.forEach(blocks::add);
        if (!draft.details.isEmpty()) {
            StringJoiner rows = new StringJoiner("\n");
            draft.details.forEach(detail -> rows.add(detail.label() + ": " + detail.value()));
            blocks.add(rows.toString());
        }
        if (draft.action != null) {
            blocks.add(draft.action.label() + ":\n" + draft.action.url());
        }
        if (draft.secondaryAction != null) {
            blocks.add(draft.secondaryAction.label() + ":\n" + draft.secondaryAction.url());
        }
        if (draft.callout != null) {
            blocks.add((draft.calloutTitle == null ? "" : draft.calloutTitle + " ") + draft.callout);
        }
        blocks.add(draft.signOff);
        StringJoiner footer = new StringJoiner("\n");
        draft.footer.forEach(footer::add);
        if (draft.footerLink != null) {
            footer.add(draft.footerLink.label() + ": " + draft.footerLink.url());
        }
        blocks.add("--\n" + draft.brand + "\n" + footer);
        return blocks.toString();
    }

    static String html(EmailDraft draft) {
        Colors colors = TONES.get(draft.tone);
        StringBuilder card = new StringBuilder();
        card.append(kickerBlock(draft, colors));
        if (draft.amount != null) {
            card.append(amountBlock(draft));
            card.append(block("<h1 style=\"margin:0;font-family:%s;font-size:20px;line-height:28px;font-weight:700;color:%s;\">%s</h1>"
                    .formatted(FONT, TEXT, escape(draft.title)), 14));
        } else {
            card.append(block("<h1 class=\"headline\" style=\"margin:0;font-family:%s;font-size:28px;line-height:36px;font-weight:700;letter-spacing:-0.4px;color:%s;\">%s</h1>"
                    .formatted(FONT, TEXT, escape(draft.title)), 18));
        }
        card.append(paragraph(draft.greeting, TEXT, 600, 20));
        draft.paragraphs.forEach(text -> card.append(paragraph(text, BODY, 400, 10)));
        if (!draft.details.isEmpty()) {
            card.append(receipt(draft));
        }
        if (draft.action != null) {
            card.append(button(draft.action));
        }
        if (draft.linkHint != null && draft.action != null) {
            card.append(linkFallback(draft));
        }
        if (draft.secondaryAction != null) {
            card.append(secondaryLink(draft.secondaryAction));
        }
        if (draft.callout != null) {
            card.append(calloutBlock(draft, colors));
        }
        card.append(block("<p style=\"margin:0;font-family:%s;font-size:15px;line-height:22px;color:%s;\">%s</p>"
                .formatted(FONT, BODY, escape(draft.signOff)), 30));
        return """
                <!doctype html>
                <html lang="%s" xmlns:o="urn:schemas-microsoft-com:office:office">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="x-apple-disable-message-reformatting">
                <meta name="color-scheme" content="light">
                <meta name="supported-color-schemes" content="light">
                <title>%s</title>
                <style>
                  @media only screen and (max-width: 600px) {
                    .page { padding: 0 !important; }
                    .card { border-radius: 0 !important; border-left: 0 !important; border-right: 0 !important; }
                    .pad { padding-left: 24px !important; padding-right: 24px !important; }
                    .headline { font-size: 24px !important; line-height: 31px !important; }
                    .amount { font-size: 36px !important; line-height: 42px !important; }
                    .button { width: 100%% !important; }
                    .button a { display: block !important; text-align: center !important; }
                  }
                </style>
                </head>
                <body style="margin:0;padding:0;background-color:%s;-webkit-text-size-adjust:100%%;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:%s;">%s</div>
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="%s" style="background-color:%s;">
                <tr><td align="center" class="page" style="padding:40px 16px;">
                <!--[if mso]><table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0"><tr><td><![endif]-->
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;">
                <tr><td class="card" bgcolor="#ffffff" style="background-color:#ffffff;border:1px solid %s;border-radius:14px;overflow:hidden;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                %s%s<tr><td style="height:36px;line-height:36px;font-size:0;">&nbsp;</td></tr>
                </table>
                </td></tr>
                %s</table>
                <!--[if mso]></td></tr></table><![endif]-->
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(draft.language.tag(), escape(draft.subject), PAGE, PAGE, escape(draft.preheader), PAGE,
                PAGE, LINE, header(draft), card, footer(draft));
    }

    // A rounded square stands in for the logo: images are blocked by default in most clients, and SVG in nearly all
    private static String header(EmailDraft draft) {
        return """
                <tr><td class="pad" bgcolor="%s" style="background-color:%s;padding:22px 44px;">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0"><tr>
                <td width="32" height="32" align="center" valign="middle" bgcolor="#ffffff" style="width:32px;height:32px;background-color:#ffffff;border-radius:9px;font-family:%s;font-size:18px;line-height:32px;font-weight:800;color:%s;">B</td>
                <td style="padding-left:12px;font-family:%s;font-size:18px;line-height:24px;font-weight:700;letter-spacing:-0.2px;color:#ffffff;">%s</td>
                </tr></table>
                </td></tr>
                <tr><td bgcolor="%s" style="height:4px;line-height:4px;font-size:0;background-color:%s;">&nbsp;</td></tr>
                """.formatted(NAVY, NAVY, FONT, BRAND, FONT, escape(draft.brand), BRAND, BRAND);
    }

    private static String kickerBlock(EmailDraft draft, Colors colors) {
        return """
                <tr><td class="pad" style="padding:32px 44px 0;">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0"><tr>
                <td width="36" height="36" align="center" valign="middle" bgcolor="%s" style="width:36px;height:36px;background-color:%s;border-radius:18px;font-family:%s;font-size:18px;line-height:36px;font-weight:700;color:%s;">%s</td>
                <td style="padding-left:12px;font-family:%s;font-size:12px;line-height:16px;font-weight:700;letter-spacing:1.6px;text-transform:uppercase;color:%s;">%s</td>
                </tr></table>
                </td></tr>
                """.formatted(colors.soft(), colors.soft(), FONT, colors.accent(), escape(draft.icon), FONT,
                colors.accent(), escape(draft.kicker));
    }

    // The amount comes first and largest: it is what the reader opens a money email to see
    private static String amountBlock(EmailDraft draft) {
        return block("""
                <div style="font-family:%s;font-size:12px;line-height:16px;font-weight:600;letter-spacing:0.4px;color:%s;">%s</div>
                <div class="amount" style="padding-top:6px;font-family:%s;font-size:44px;line-height:50px;font-weight:700;letter-spacing:-1.2px;%scolor:%s;">%s</div>"""
                .formatted(FONT, MUTED, escape(draft.amountLabel), FONT, FIGURES, TEXT, escape(draft.amount)), 24);
    }

    private static String block(String content, int top) {
        return "<tr><td class=\"pad\" style=\"padding:%dpx 44px 0;\">%s</td></tr>\n".formatted(top, content);
    }

    private static String paragraph(String text, String color, int weight, int top) {
        return block("<p style=\"margin:0;font-family:%s;font-size:16px;line-height:26px;font-weight:%d;color:%s;\">%s</p>"
                .formatted(FONT, weight, color, escape(text)), top);
    }

    // The figures, set as the lines of a voucher between perforated rules
    private static String receipt(EmailDraft draft) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < draft.details.size(); i++) {
            EmailDraft.Detail detail = draft.details.get(i);
            String border = i == 0 ? "" : "border-top:1px dotted %s;".formatted(PERFORATION);
            String valueFont = detail.code() ? MONO + ";letter-spacing:0.6px" : FONT;
            rows.append("""
                    <tr>
                    <td valign="top" style="%spadding:12px 14px 12px 0;font-family:%s;font-size:13px;line-height:20px;white-space:nowrap;color:%s;">%s</td>
                    <td valign="top" align="right" style="%spadding:12px 0;font-family:%s;font-size:14px;line-height:20px;font-weight:600;%scolor:%s;word-break:break-word;">%s</td>
                    </tr>
                    """.formatted(border, FONT, MUTED, escape(detail.label()), border, valueFont, FIGURES, TEXT,
                    escape(detail.value())));
        }
        return block("""
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="%s" style="background-color:%s;border-top:2px dashed %s;border-bottom:2px dashed %s;">
                <tr><td style="padding:6px 20px;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                %s</table>
                </td></tr>
                </table>""".formatted(PANEL, PANEL, PERFORATION, PERFORATION, rows), 26);
    }

    // Padding on the link, not the cell, so the whole button is clickable everywhere but Outlook, which still gets a
    // working link and a solid background
    private static String button(EmailDraft.Action action) {
        return block("""
                <table role="presentation" class="button" cellspacing="0" cellpadding="0" border="0"><tr>
                <td align="center" bgcolor="%s" style="background-color:%s;border-radius:10px;mso-padding-alt:15px 30px;">
                <a href="%s" target="_blank" rel="noopener" style="display:inline-block;padding:15px 30px;font-family:%s;font-size:15px;line-height:20px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:10px;">%s&nbsp;&rarr;</a>
                </td></tr></table>""".formatted(BRAND, BRAND, escape(action.url()), FONT, escape(action.label())), 30);
    }

    private static String linkFallback(EmailDraft draft) {
        return block("""
                <p style="margin:0;font-family:%s;font-size:12px;line-height:19px;color:%s;">%s<br>
                <a href="%s" target="_blank" rel="noopener" style="color:%s;word-break:break-all;">%s</a></p>"""
                .formatted(FONT, MUTED, escape(draft.linkHint), escape(draft.action.url()), BRAND,
                        escape(draft.action.url())), 18);
    }

    private static String secondaryLink(EmailDraft.Action action) {
        return block("""
                <a href="%s" target="_blank" rel="noopener" style="font-family:%s;font-size:14px;line-height:20px;font-weight:600;color:%s;">%s&nbsp;&rarr;</a>"""
                .formatted(escape(action.url()), FONT, BRAND, escape(action.label())), 18);
    }

    private static String calloutBlock(EmailDraft draft, Colors colors) {
        boolean calm = draft.tone == EmailDraft.Tone.SUCCESS || draft.tone == EmailDraft.Tone.INFO;
        String background = calm ? PANEL : colors.soft();
        String edge = calm ? PERFORATION : colors.accent();
        String title = draft.calloutTitle == null ? "" : """
                <strong style="display:block;color:%s;">%s</strong>""".formatted(TEXT, escape(draft.calloutTitle));
        return block("""
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="%s" style="background-color:%s;border-left:3px solid %s;">
                <tr><td style="padding:14px 18px;font-family:%s;font-size:14px;line-height:22px;color:%s;">%s%s</td></tr>
                </table>""".formatted(background, background, edge, FONT, BODY, title, escape(draft.callout)), 28);
    }

    private static String footer(EmailDraft draft) {
        StringBuilder lines = new StringBuilder();
        draft.footer.forEach(line -> lines.append("""
                <p style="margin:0 0 8px;font-family:%s;font-size:12px;line-height:18px;color:%s;">%s</p>
                """.formatted(FONT, MUTED, escape(line))));
        if (draft.footerLink != null) {
            lines.append("""
                    <p style="margin:0 0 8px;font-family:%s;font-size:12px;line-height:18px;font-weight:600;"><a href="%s" target="_blank" rel="noopener" style="color:%s;">%s</a></p>
                    """.formatted(FONT, escape(draft.footerLink.url()), BRAND, escape(draft.footerLink.label())));
        }
        return """
                <tr><td class="pad" align="center" style="padding:28px 44px 8px;text-align:center;">
                <p style="margin:0 0 10px;font-family:%s;font-size:13px;line-height:18px;font-weight:700;color:%s;">%s</p>
                %s</td></tr>
                """.formatted(FONT, NAVY, escape(draft.brand), lines);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
