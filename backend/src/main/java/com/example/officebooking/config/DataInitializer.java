package com.example.officebooking.config;

import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.AppSettingsRepository;
import com.example.officebooking.settings.LimitRule;
import com.example.officebooking.settings.LimitRuleRepository;
import com.example.officebooking.settings.PeriodType;
import com.example.officebooking.user.Role;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Идемпотентная инициализация при старте:
 *  - строка настроек и правила лимитов (из переменных окружения, если ещё не созданы);
 *  - первый MANAGER из BOOTSTRAP_MANAGER_EMAIL / BOOTSTRAP_MANAGER_PASSWORD (если менеджеров нет);
 *  - демо-сотрудники при APP_DEMO_DATA=true (15 человек, пароль из APP_DEMO_PASSWORD).
 */
@Component
public class DataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppProperties props;
    private final AppSettingsRepository settingsRepo;
    private final LimitRuleRepository ruleRepo;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DataInitializer(AppProperties props, AppSettingsRepository settingsRepo, LimitRuleRepository ruleRepo,
                           UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.props = props;
        this.settingsRepo = settingsRepo;
        this.ruleRepo = ruleRepo;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initSettings();
        initRules();
        initManager();
        initDemoData();
    }

    private void initSettings() {
        if (settingsRepo.existsById(AppSettings.SINGLETON_ID)) return;
        AppSettings s = new AppSettings();
        s.setOfficeCapacity(Math.max(1, props.officeCapacity()));
        s.setAllowSameDayBooking(props.allowSameDayBooking());
        s.setCancellationDeadlineHours(Math.max(0, props.cancellationDeadlineHours()));
        s.setWorkingDaysSet(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        s.setTimezone(props.timezone());
        s.setUpdatedAt(Instant.now(clock));
        settingsRepo.save(s);
        log.info("Созданы начальные настройки приложения (мест: {})", s.getOfficeCapacity());
    }

    private void initRules() {
        createRuleIfMissing(PeriodType.WEEKLY, 1);
        createRuleIfMissing(PeriodType.MONTHLY, 4);
    }

    private void createRuleIfMissing(PeriodType type, int max) {
        if (ruleRepo.findByPeriodType(type).isPresent()) return;
        LimitRule r = new LimitRule();
        r.setPeriodType(type);
        r.setMaxDays(max);
        r.setEnabled(true);
        r.setUpdatedAt(Instant.now(clock));
        ruleRepo.save(r);
    }

    private void initManager() {
        if (users.existsByRole(Role.MANAGER)) return;
        AppProperties.Bootstrap b = props.bootstrap();
        if (b.managerEmail() == null || b.managerEmail().isBlank()
                || b.managerPassword() == null || b.managerPassword().isBlank()) {
            log.warn("В системе нет ни одного MANAGER. Задайте BOOTSTRAP_MANAGER_EMAIL и BOOTSTRAP_MANAGER_PASSWORD и перезапустите приложение.");
            return;
        }
        if (b.managerPassword().length() < 8) {
            throw new IllegalStateException("BOOTSTRAP_MANAGER_PASSWORD должен содержать не менее 8 символов");
        }
        createUser(b.managerFirstName(), b.managerLastName(), b.managerEmail(), b.managerPassword(), Role.MANAGER);
        log.info("Создан первый руководитель: {}", b.managerEmail());
    }

    private void initDemoData() {
        AppProperties.Bootstrap b = props.bootstrap();
        if (!b.demoData()) return;
        if (b.demoPassword() == null || b.demoPassword().length() < 8) {
            throw new IllegalStateException("При APP_DEMO_DATA=true задайте APP_DEMO_PASSWORD (не менее 8 символов)");
        }
        String[] first = {"Айбек", "Бермет", "Данияр", "Элина", "Фархад", "Гульнара", "Нурлан", "Айжан", "Тимур",
                "Саида", "Руслан", "Мээрим", "Эльдар", "Чолпон", "Азамат"};
        String[] last = {"Асанов", "Бекова", "Джумабаев", "Эркинова", "Жунусов", "Калиева", "Мамбетов", "Орозова",
                "Сыдыков", "Токтомушева", "Уралиев", "Чынгышева", "Шамшиев", "Юсупова", "Абдыкадыров"};
        for (int i = 0; i < 15; i++) {
            String email = String.format("employee%02d@example.com", i + 1);
            if (!users.existsByEmailIgnoreCase(email)) {
                createUser(first[i], last[i], email, b.demoPassword(), Role.EMPLOYEE);
            }
        }
        log.info("Демо-сотрудники готовы (employee01..employee15@example.com)");
    }

    private void createUser(String firstName, String lastName, String email, String password, Role role) {
        User u = new User();
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setEnabled(true);
        u.setCreatedAt(Instant.now(clock));
        u.setUpdatedAt(Instant.now(clock));
        users.save(u);
    }
}
