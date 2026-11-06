package com.fashion.Riskyc.service;

import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Enterprise;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Seller-facing transactional email — invite credentials and application
 * decisions. Every send is wrapped so a misconfigured/unset SMTP host never
 * breaks the calling flow (an enterprise still gets approved and provisioned
 * even if the email itself can't go out — see each method's catch block);
 * it's logged instead, same philosophy as this project's existing
 * best-effort notification channels (SmsService, PushNotificationService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.site.url}")
    private String siteUrl;

    public void sendSellerInvite(Enterprise enterprise, AdminUser owner, String tempPassword) {
        String subject = "You're approved to sell on Riskyc Fashion";
        String html = """
                <p>Hi %s,</p>
                <p><strong>%s</strong> has been approved to sell on Riskyc Fashion.</p>
                <p>Sign in with these temporary credentials — you'll be asked to set your own password right away:</p>
                <p>
                    Email: <strong>%s</strong><br>
                    Temporary password: <strong>%s</strong>
                </p>
                <p><a href="%s/admin/login">Sign in to your seller panel</a></p>
                <p>If the link doesn't work, go to %s/admin/login directly.</p>
                <p style="color:#555;font-size:13px;border-top:1px solid #eee;padding-top:12px;margin-top:20px;">
                    <strong>Before your first sale:</strong> open <em>Payments</em> in your seller panel and enter your
                    own Orange Money and/or MTN Mobile Money numbers. Your customers pay you directly on those numbers —
                    Riskyc Fashion never receives your money. Until they're set, your products are visible but can't be
                    bought online.<br><br>
                    <strong>You run your own orders:</strong> when a customer uploads proof of payment, check it against
                    your own account and confirm it in <em>Orders</em>, then pack it in <em>Packing</em> and send it with
                    your own delivery team (set them up in <em>Delivery team</em>).
                </p>
                """.formatted(owner.getFirstName(), escape(enterprise.getName()), owner.getEmail(), tempPassword, siteUrl, siteUrl);
        send(owner.getEmail(), subject, html);
    }

    public void sendApplicationDecision(Enterprise enterprise, boolean approved, String reason) {
        String subject = approved
                ? "Your Riskyc Fashion seller application was approved"
                : "About your Riskyc Fashion seller application";
        String html = approved
                ? "<p>Good news — <strong>%s</strong> has been approved. A separate email with your login details is on its way.</p>".formatted(escape(enterprise.getName()))
                : """
                        <p>Thanks for applying to sell on Riskyc Fashion as <strong>%s</strong>.</p>
                        <p>We're not able to approve this application right now%s.</p>
                        """.formatted(escape(enterprise.getName()), reason != null && !reason.isBlank() ? ": " + escape(reason) : "");
        send(enterprise.getContactEmail(), subject, html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Could not send email '{}' to {} (SMTP likely not configured — see spring.mail.* in application.properties)", subject, to, e);
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
