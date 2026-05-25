use spacetimedb::{table, reducer, ReducerContext, Table, log};

#[table(accessor = person, public)]
pub struct Person {
    #[primary_key]
    pub name: String,
    pub age: u32,
}

#[reducer]
pub fn add_person(ctx: &ReducerContext, name: String, age: u32) {
    let person = ctx.db.person().insert(Person { name, age });
    log::info!("Person with name {} and age {} inserted", person.name, person.age)
}
