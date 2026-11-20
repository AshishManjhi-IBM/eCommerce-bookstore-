package com.bookstore.config;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.catalog.BookFormat;
import com.bookstore.catalog.Brand;
import com.bookstore.catalog.BrandRepository;
import com.bookstore.catalog.Category;
import com.bookstore.catalog.CategoryRepository;
import com.bookstore.catalog.Product;
import com.bookstore.catalog.ProductRepository;
import com.bookstore.catalog.Writer;
import com.bookstore.catalog.WriterRepository;
import com.bookstore.common.AppProperties;
import com.bookstore.member.Role;
import com.bookstore.member.User;
import com.bookstore.member.UserRepository;
import com.bookstore.order.Coupon;
import com.bookstore.order.CouponRepository;
import com.bookstore.store.CatalogEntity;
import com.bookstore.store.CatalogRepository;
import com.bookstore.store.Store;
import com.bookstore.store.StorePolicy;
import com.bookstore.store.StorePolicyRepository;
import com.bookstore.store.StoreRepository;

/** Sample data for a fresh database: admin user, a store, brands, writers, books and a coupon. */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final StoreRepository stores;
    private final StorePolicyRepository policies;
    private final CatalogRepository catalogs;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final WriterRepository writers;
    private final ProductRepository products;
    private final CouponRepository coupons;

    public DataSeeder(AppProperties props, UserRepository users, PasswordEncoder encoder, StoreRepository stores,
            StorePolicyRepository policies, CatalogRepository catalogs, CategoryRepository categories,
            BrandRepository brands, WriterRepository writers, ProductRepository products,
            CouponRepository coupons) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.stores = stores;
        this.policies = policies;
        this.catalogs = catalogs;
        this.categories = categories;
        this.brands = brands;
        this.writers = writers;
        this.products = products;
        this.coupons = coupons;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled() || users.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setFirstName("Store");
        admin.setLastName("Admin");
        admin.setEmail(props.seed().adminEmail());
        admin.setPasswordHash(encoder.encode(props.seed().adminPassword()));
        admin.setRole(Role.ADMIN);
        users.save(admin);

        Store store = new Store();
        store.setName("E-Bookstore");
        store = stores.save(store);
        StorePolicy policy = new StorePolicy();
        policy.setStoreId(store.getId());
        policy.setTitle("Cancellation");
        policy.setDescription("Orders can be cancelled within 48 hours of placing them, before they ship.");
        policies.save(policy);
        CatalogEntity catalog = new CatalogEntity();
        catalog.setStoreId(store.getId());
        catalog.setName("Main catalogue");
        catalog = catalogs.save(catalog);

        Coupon welcome = new Coupon();
        welcome.setCode("WELCOME10");
        welcome.setPercentOff(10);
        coupons.save(welcome);

        Brand penguin = brands.save(new Brand("Penguin Books"));
        Brand harper = brands.save(new Brand("HarperCollins"));
        Brand bloomsbury = brands.save(new Brand("Bloomsbury"));
        Brand scholastic = brands.save(new Brand("Scholastic"));

        Writer austen = writers.save(new Writer("Jane Austen",
                "English novelist known for her witty novels of manners set among the landed gentry."));
        Writer christie = writers.save(new Writer("Agatha Christie",
                "The best-selling mystery writer of all time, creator of Hercule Poirot and Miss Marple."));
        Writer herbert = writers.save(new Writer("Frank Herbert",
                "American science fiction author, best known for the Dune series."));
        Writer tolkien = writers.save(new Writer("J.R.R. Tolkien",
                "English author and philologist who wrote The Hobbit and The Lord of the Rings."));
        Writer rowling = writers.save(new Writer("J.K. Rowling",
                "British author of the Harry Potter fantasy series."));
        Writer clear = writers.save(new Writer("James Clear",
                "Writer and speaker focused on habits, decision making and continuous improvement."));
        Writer sagan = writers.save(new Writer("Carl Sagan",
                "American astronomer and science communicator."));

        long catalogId = catalog.getId();
        List<Product> books = List.of(
                book("Pride and Prejudice", austen, penguin, "Romance", BookFormat.PAPERBACK, "299", 40, null,
                        "Romance,Classic", "9780141439518",
                        "A witty story of manners, marriage and misjudgement.", catalogId),
                book("Emma", austen, penguin, "Romance", BookFormat.HARDCOVER, "449", 25, 4, "Romance,Classic",
                        "9780141439587", "A young woman's matchmaking goes comically astray.", catalogId),
                book("Murder on the Orient Express", christie, harper, "Mystery", BookFormat.PAPERBACK, "349", 60, 2,
                        "Mystery,Crime", "9780062693662", "Hercule Poirot investigates a murder on a snowbound train.",
                        catalogId),
                book("And Then There Were None", christie, harper, "Mystery", BookFormat.PAPERBACK, "329", 50, null,
                        "Mystery,Thriller", "9780062073488", "Ten strangers, one island, and no way out.",
                        catalogId),
                book("Dune", herbert, penguin, "Science Fiction", BookFormat.PAPERBACK, "499", 35, null,
                        "Science Fiction,Epic", "9780441172719", "A desert planet, a prophecy and a war for spice.",
                        catalogId),
                book("The Hobbit", tolkien, harper, "Fantasy", BookFormat.HARDCOVER, "599", 30, 3,
                        "Fantasy,Adventure", "9780547928227", "Bilbo Baggins joins a quest to reclaim a lost treasure.",
                        catalogId),
                book("Harry Potter and the Philosopher's Stone", rowling, bloomsbury, "Young Adult",
                        BookFormat.PAPERBACK, "399", 80, 2, "Fantasy,Young Adult", "9781408855652",
                        "A boy discovers he is a wizard on his eleventh birthday.", catalogId),
                book("Harry Potter and the Chamber of Secrets", rowling, bloomsbury, "Young Adult",
                        BookFormat.EBOOK, "249", 100, 0, "Fantasy,Young Adult", "9781408855669",
                        "A hidden chamber and a monster at Hogwarts.", catalogId),
                book("Atomic Habits", clear, penguin, "Self-help", BookFormat.PAPERBACK, "499", 70, null,
                        "Self-help,Productivity", "9780735211292", "Tiny changes that build remarkable results.",
                        catalogId),
                book("Cosmos", sagan, scholastic, "Science", BookFormat.PAPERBACK, "379", 20, 4, "Science,Astronomy",
                        "9780345539434", "A personal voyage through the universe.", catalogId));
        products.saveAll(books);
        log.info("Seeded {} books. Admin login: {}", books.size(), props.seed().adminEmail());
    }

    private Product book(String title, Writer writer, Brand brand, String category, BookFormat format, String price,
            int stock, Integer deliveryDays, String tags, String isbn, String blurb, Long catalogId) {
        Category cat = categories.findAll().stream().filter(c -> c.getName().equals(category)).findFirst()
                .orElseThrow();
        Product p = new Product();
        p.setTitle(title);
        p.setDescription(blurb);
        p.setBackCoverBlurb(blurb);
        p.setIsbn(isbn);
        p.setLanguage("English");
        p.setBookFormat(format);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setDeliveryDays(deliveryDays);
        p.setGenreTags(tags);
        p.setCategory(cat);
        p.setBrand(brand);
        p.setWriter(writer);
        p.setCatalogId(catalogId);
        return p;
    }
}
