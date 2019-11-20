package dao.oldDaoWithoutInterfaces;

import dao.interfaces.RowMapper;
import dto.UserDto;
import model.User;
import singletone.ConnectionService;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.*;

public class UsersRepository {
    private Connection connection;
    public UsersRepository() {
        connection = ConnectionService.getConnection();
    }
    private RowMapper<User> userFindRowMapper = row -> {
        Long id = row.getLong("id");

        String name = row.getString("name");
        String password = row.getString("password");
        String nick = row.getString("nickname");
        String mail = row.getString("email");
        Date birthday = row.getObject(6, Date.class);
        LocalDateTime registration = row.getObject(7,LocalDateTime.class);
        Long posts = row.getLong(8);
        String photoPath = row.getString("photo_path");
        return new User(id,name,password,nick,mail, birthday, registration,posts,photoPath);
    };
    //language=SQL
    public Optional<User> findById(Long id) {
        User user = null;
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?")){
            statement.setLong(1,id);
            ResultSet resultSet = statement.executeQuery();
            //Если соответстующая строка найдена,обрабатываем её c помощью userRowMapper.
            //Соответствунно получаем объект User.
            if (resultSet.next()) {
                user = userFindRowMapper.mapRow(resultSet);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.ofNullable(user);
    }
    public Optional<User> find(String name) {
        User user = null;
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE name = ?")){
            statement.setString(1,name);
            ResultSet resultSet = statement.executeQuery();
            //Если соответстующая строка найдена,обрабатываем её c помощью userRowMapper.
            //Соответствунно получаем объект User.
            if (resultSet.next()) {
                user = userFindRowMapper.mapRow(resultSet);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.ofNullable(user);
    }

    public void save(User model) {
        //Создаём новый объект PreparedStatement,с соотвествующим запросом для сохранния пользователя
        //Использование try-with-resources необходимо для гарантированного закрытия statement,вне зависимости от успешности операции.
        //Аргумент Statement.RETURN_GENERATED_KEYS даёт возможность хранения сгенерированных id (ключей)  внутри statement.
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (name, password, nickname, email, birthday, registration_date, posts,photo_path) VALUES (?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);) {
            statement.setString(1,model.getName());
            statement.setString(2,model.getPassword());
            statement.setString(3,model.getNick());
            statement.setString(4,model.getEmail());
            statement.setObject(5,model.getBirth_date());
            statement.setObject(6, model.getRegiStrationDate());
            statement.setLong(7,model.getCountPosts());
            statement.setString(8,model.getPhotoPath());
            //Выполняем запрос и сохраняем колличество изменённых строк
            int updRows = statement.executeUpdate();
            if (updRows == 0) {
                //Если ничего не было изменено, значит возникла ошибка
                //Возбуждаем соответсвующее исключений
                throw new SQLException();
            }
            //Достаём созданное Id пользователя
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
    public Optional<UserDto> findDtoById(Long id) {
        UserDto userInfo = null;
        try (PreparedStatement statement = connection.prepareStatement("SELECT nickname,photo_path FROM user_view WHERE user_view.id = ?")){
            statement.setLong(1,id);
            ResultSet resultSet = statement.executeQuery();
            //Если соответстующая строка найдена,обрабатываем её c помощью userRowMapper.
            //Соответствунно получаем объект User.
            if (resultSet.next()) {
                userInfo = userDtoRowMapper.mapRow(resultSet);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.ofNullable(userInfo);
    }
    private RowMapper<UserDto> userDtoRowMapper = row -> {
        String nick = row.getString("nickname");
        String photoPath = row.getString("photo_path");
        return new UserDto(nick,photoPath);
    };

    public Map<Long, UserDto> findAllDtoBySet(Set<Long> userIds) {
        Map<Long, UserDto> usersInfo = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT nickname,photo_path from user_view where user_view.id= ?")) {
            for (Long id : userIds) {
                statement.setLong(1, id);
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    UserDto userDto = userDtoRowMapper.mapRow(resultSet);
                    usersInfo.put(id, userDto);
                }
                else{
                    throw new IllegalStateException
                            ("Ненайден существующий у сообщения пользователь");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return usersInfo;
    }
}