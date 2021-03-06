package dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dao.interfaces.PostRepository;
import dao.interfaces.RowMapper;
import model.Category;
import model.Post;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import singletone.ConnectionService;

import java.net.InetAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class PostRepositoryImpl implements PostRepository {
    private ObjectMapper objectMapper;
    private Connection connection;

    public PostRepositoryImpl() {
        this.connection = ConnectionService.getConnection();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Optional<Post> find(Long id) {
        Post post = null;
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM post WHERE id = ?")) {
            statement.setLong(1, id);
            ResultSet resultSet = statement.executeQuery();
            //Если соответстующая строка найдена,обрабатываем её c помощью userRowMapper.
            //Соответствунно получаем объект User.
            if (resultSet.next()) {
                post = postRowMapper.mapRow(resultSet);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.ofNullable(post);
    }

    @Override
    public void save(Post model) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO post(name, text, category, photo_path, publication_date, show_auth, author_id) " +
                        "VALUES (?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);) {
            statement.setString(1, model.getName());
            statement.setString(2, model.getText());
            statement.setString(3, model.getCategory().toString());
            statement.setString(4, model.getPhotoPath());
            statement.setObject(5, model.getPublication());
            statement.setObject(6, model.getShowAuthor());
            statement.setLong(7, model.getAuth_id());
            //Выполняем запрос и сохраняем колличество изменённых строк
            int updRows = statement.executeUpdate();
            if (updRows == 0) {
                //Если ничего не было изменено, значит возникла ошибка
                //Возбуждаем соответсвующее исключений
                throw new SQLException();
            }
            //Достаём созданное Id поста
            try (ResultSet set = statement.getGeneratedKeys();) {
                //Если id  существет,обновляем его у подели.
                if (set.next()) {
                    model.setId(set.getLong(1));
                } else {
                    //Модель сохранилась но не удаётся получить сгенерированный id
                    //Возбуждаем соответвующее исключение
                    throw new SQLException();
                }
            }

        } catch (SQLException e) {
            //Если сохранений провалилось, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
    }

    public void saveToElastic(Post model, Client client, ObjectMapper objectMapper) {
        try {
            String value = objectMapper.writeValueAsString(model);
            IndexResponse response = client.prepareIndex("test_ind", "_doc", String.valueOf(model.getId()))
                    .setSource(value, XContentType.JSON)
                    .get();
            System.out.println(response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void updateInElastic(Post model, Client client, ObjectMapper objectMapper) {
//        String value = null;
//        try {
//            value = objectMapper.writeValueAsString(model);
//        } catch (JsonProcessingException e) {
//            throw new IllegalStateException(e);
//        }
//        UpdateRequest updateRequest = new UpdateRequest();
//        updateRequest.index("index");
//        updateRequest.type("_doc");
//        updateRequest.id(String.valueOf(model.getId()));
//        @// TODO: 24/11/2019 Почему всё так с ошибочками и почему depricated
//        updateRequest.doc();
//        try {
//            client.update(updateRequest).get();
//        } catch (InterruptedException e) {
//            throw new IllegalStateException(e);
//        } catch (ExecutionException e) {
//            throw new IllegalStateException(e);
//        }
    }

    @Override
    public void deleteInElastic(Post model, Client client, ObjectMapper objectMapper) {

    }

    @Override
    public List<Post> findAllWithNameByQuery(String query) {
        List<Post> result = new ArrayList<>();

        //Создаём новый объект Statement
        //Использование try-with-resources необходимо для арантированного закрытия statement,
        // вне зависимости от успешности операции.
        String SQL_findByName = "select * from post WHERE name like ?";

        try (PreparedStatement statement = connection.prepareStatement(SQL_findByName)) {
            statement.setString(1, "%" + query + "%");
            //ResultSet - итерируемый объект.
            //Пока есть что доставать, идём по нему и подаём строки в userRowMapper,
            // который возвращает нам готовый объект User.
            //Добавляем полученный объект в ArrayList.
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Post post = postRowMapper.mapRow(resultSet);
                result.add(post);
            }
        } catch (SQLException e) {
            //Если операция провалилась, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
        //Возвращаем полученный в результате операции ArrayList
        return result;
    }

    @Override
    public List<Post> findAllWithNameByQueryAndCategory(String query, String category) {
        List<Post> result = new ArrayList<>();

        //Создаём новый объект Statement
        //Использование try-with-resources необходимо для арантированного закрытия statement,
        // вне зависимости от успешности операции.
        String SQL_findByCategory = "select * from post WHERE name like ? AND category = ?";

        try (PreparedStatement statement = connection.prepareStatement(SQL_findByCategory)) {
            statement.setString(1, "%" + query + "%");
            statement.setObject(2, category);
            //ResultSet - итерируемый объект.
            //Пока есть что доставать, идём по нему и подаём строки в userRowMapper,
            // который возвращает нам готовый объект User.
            //Добавляем полученный объект в ArrayList.
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Post post = postRowMapper.mapRow(resultSet);
                result.add(post);
            }
        } catch (SQLException e) {
            //Если операция провалилась, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
        //Возвращаем полученный в результате операции ArrayList
        return result;
    }

    @Override
    public List<Post> findAllWithTextByQuery(String query) {
        List<Post> result = new ArrayList<>();

        //Создаём новый объект Statement
        //Использование try-with-resources необходимо для арантированного закрытия statement,
        // вне зависимости от успешности операции.
        String SQL_findByText = "select * from post WHERE text like ?";

        try (PreparedStatement statement = connection.prepareStatement(SQL_findByText)) {
            statement.setString(1, "%" + query + "%");
            //ResultSet - итерируемый объект.
            //Пока есть что доставать, идём по нему и подаём строки в userRowMapper,
            // который возвращает нам готовый объект User.
            //Добавляем полученный объект в ArrayList.
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Post post = postRowMapper.mapRow(resultSet);
                result.add(post);
            }
        } catch (SQLException e) {
            //Если операция провалилась, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
        //Возвращаем полученный в результате операции ArrayList
        return result;
    }

    @Override
    public List<Post> findAllWithTextByQueryAndCategory(String query, String category) {
        List<Post> result = new ArrayList<>();

        //Создаём новый объект Statement
        //Использование try-with-resources необходимо для арантированного закрытия statement,
        // вне зависимости от успешности операции.
        String SQL_findByCategory = "select * from post WHERE text like ? AND category = ?";

        try (PreparedStatement statement = connection.prepareStatement(SQL_findByCategory)) {
            statement.setString(1, "%" + query + "%");
            statement.setObject(2, category);
            //ResultSet - итерируемый объект.
            //Пока есть что доставать, идём по нему и подаём строки в userRowMapper,
            // который возвращает нам готовый объект User.
            //Добавляем полученный объект в ArrayList.
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Post post = postRowMapper.mapRow(resultSet);
                result.add(post);
            }
        } catch (SQLException e) {
            //Если операция провалилась, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
        //Возвращаем полученный в результате операции ArrayList
        return result;
    }

    //language=sql
    public static final String SQLUpdate = "UPDATE post SET name = ?, text = ?, category = ?, photo_path = ?, show_auth = ?  WHERE id = ?";

    @Override
    public void update(Post model) {
        try (PreparedStatement statement = connection.prepareStatement(SQLUpdate)) {
            //На место соответвующих вопросительных знаков уставнавливаем параметры модели, которую мы хотим обновить
            statement.setString(1, model.getName());
            statement.setString(2, model.getText());
            statement.setString(3, model.getCategory().toString());
            statement.setString(4, model.getPhotoPath());
            statement.setObject(5, model.getShowAuthor());
            statement.setLong(6, model.getId());
            //Выполняем запрос и сохраняем колличество изменённых строк
            int updRows = statement.executeUpdate();

            if (updRows == 0) {
                //Если ничего не было изменено, значит возникла ошибка
                //Возбуждаем соответсвующее исключений
                throw new SQLException();
            }
        } catch (SQLException e) {
            //Если обноление провалилось, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void delete(Long id) {
        if (id < 0L) throw new IllegalArgumentException();
        /* Мы выполняем sql-запрос, удаляя строку из таблицы по параметру id. */
        //Создаём новый объект Statement
        //Использование try-with-resources необходимо для гарантированного закрытия statement,вне зависимости от успешности операции.
        try (Statement statement = connection.createStatement()) {
            //Выолняем запрос и получаем колличество изменённых строк
            int updRows = statement.executeUpdate("DELETE from post where id = " + id + ";");
            if (updRows == 0) {
                //Если ничего не было изменено, значит возникла ошибка
                //Возбуждаем соответсвующее исключений
                throw new SQLException();
            }
        } catch (SQLException e) {
            //Если удаление провалилось, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Post> findAll() {
        return null;
    }

    @Override
    public List<Post> findAllByCategory(String category, Long offset) {
        List<Post> result = new ArrayList<>();

        //Создаём новый объект Statement
        //Использование try-with-resources необходимо для арантированного закрытия statement,
        // вне зависимости от успешности операции.
        String SQL_findByCategory = "select * from post WHERE category = (?) ORDER BY id DESC limit 5 offset (?);";

        try (PreparedStatement statement = connection.prepareStatement(SQL_findByCategory)) {
            statement.setString(1, category);
            statement.setObject(2, offset);
            //ResultSet - итерируемый объект.
            //Пока есть что доставать, идём по нему и подаём строки в userRowMapper,
            // который возвращает нам готовый объект User.
            //Добавляем полученный объект в ArrayList.
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Post post = postRowMapper.mapRow(resultSet);
                result.add(post);
            }
        } catch (SQLException e) {
            //Если операция провалилась, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
        //Возвращаем полученный в результате операции ArrayList
        return result;
    }

    @Override
    public List<Post> findAllByAuthorId(Long id) {
        List<Post> result = new ArrayList<>();

        //Создаём новый объект Statement
        //Использование try-with-resources необходимо для арантированного закрытия statement,
        // вне зависимости от успешности операции.
        String SQL_findBy_Author_ID = "select * from post WHERE author_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(SQL_findBy_Author_ID)) {
            statement.setLong(1, id);
            //ResultSet - итерируемый объект.
            //Пока есть что доставать, идём по нему и подаём строки в userRowMapper,
            // который возвращает нам готовый объект User.
            //Добавляем полученный объект в ArrayList.
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Post post = postRowMapper.mapRow(resultSet);
                result.add(post);
            }
        } catch (SQLException e) {
            //Если операция провалилась, обернём пойманное исключение в непроверяемое и пробросим дальше(best-practise)
            throw new IllegalStateException(e);
        }
        //Возвращаем полученный в результате операции ArrayList
        return result;
    }

    private RowMapper<Post> postRowMapper = row -> {
        Long id = row.getLong("id");
        String name = row.getString("name");
        String text = row.getString("text");
        Category category = Category.valueOf(row.getString("category"));
        String photoPath = row.getString("photo_path");
        LocalDateTime date = row.getObject(6, LocalDateTime.class);
        Boolean bool = row.getObject(7, Boolean.class);
        Long author_id = row.getLong(8);
        return new Post(id, name, text, category, photoPath, date, bool, author_id);
    };
}
